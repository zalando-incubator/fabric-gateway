package ie.zalando.fabric.gateway.service

import cats.data.Validated._
import cats.data._
import cats.implicits._
import ie.zalando.fabric.gateway.models.HttpModels.ValidationRequest
import ie.zalando.fabric.gateway.models.SynchDomain.{HttpVerb, Options}
import ie.zalando.fabric.gateway.models.ValidationDomain.{DecisionStatus, RejectionReason, ResourceDetails, ValidationCorsConfig}
import ie.zalando.fabric.gateway.util.Util.parseCorsUri

import scala.util.{Failure, Success, Try}

object ResourcePersistenceValidations {

  sealed trait GatewayValidation {
    def errorMessage: String
  }

  case class PathContainsWildcardSyntaxInMiddle(invalidPath: String) extends GatewayValidation {
    def errorMessage: String = s"$invalidPath is invalid. A Path can only contain `**` as the last element in the path"
  }

  case object NoPathsDefined extends GatewayValidation {
    def errorMessage: String = "There must be at least 1 path defined"
  }

  case object StacksetIntegrationDefinedWithServices extends GatewayValidation {
    def errorMessage: String =
      "You cannot define services with the `x-fabric-service` key and also set external management using `x-external-service-provider`"
  }

  case object NoStackSetIntegrationAndNoServicesDefined extends GatewayValidation {
    def errorMessage: String =
      "You must have 1 of the `x-fabric-service` or `x-external-service-provider` keys defined"
  }

  case class CorsDefinedWithOptionsRoute(route: String) extends GatewayValidation {
    def errorMessage: String =
      s"You may not define an options endpoint when cors support is configured. Options endpoint found under '$route'"
  }

  case object CorsDefinedWithWildcardAllowedOrigin extends GatewayValidation {
    def errorMessage: String = "You may not use * in your cors allowed origins"
  }

  case class CorsHostnamesInvalid(hostname: String) extends GatewayValidation {
    def errorMessage: String = s"The provided cors hostname '$hostname' is invalid"
  }

  case class ResourceNameTooLong(name: String, namespace: String) extends GatewayValidation {
    def errorMessage: String =
      s"Concatenated resource name and namespace length is capped at 60 characters: $name and $namespace are ${name.length + namespace.length} chars long"
  }

  type ValidationResult[A] = ValidatedNel[GatewayValidation, A]

  def isValid(validationRequest: ValidationRequest): DecisionStatus = {
    val pathVerbPairs: List[(String, HttpVerb)] = validationRequest.resource.paths.toList.flatMap {
      case (path, ops) => ops.map { case (verb, _) => (path, verb) }
    }
    (
      validateNameLength(validationRequest.name, validationRequest.namespace),
      validatePathsNonEmpty(validationRequest.resource),
      validationRequest.resource.paths.keys.toList.map(validateStarStarPathPosition).sequence,
      validateStackSetIntegration(validationRequest.hasExternallyManagedServices, validationRequest.definedServiceCount),
      validationRequest.corsConfig
        .map(_.allowedOrigins.toList.map(hostname => validateCorsUri(hostname)))
        .getOrElse(List.empty)
        .sequence,
      pathVerbPairs.map {
        case (path, verb) => validateCorsOptionsRoutes(validationRequest.corsConfig, path, verb)
      }.sequence
    ).mapN((_, _, _, _, _, _) => false) match {
      case Valid(notRejected) => DecisionStatus(notRejected)
      case Invalid(reasons) =>
        DecisionStatus(
          rejected = true,
          reasons = reasons.map(v => RejectionReason(v.errorMessage)).toList
        )
    }
  }

  def validateStarStarPathPosition(path: String): ValidationResult[String] = {
    val idx = path.indexOf("**")
    if (idx != -1 && (idx != (path.length - 2) || !path.endsWith("/**"))) PathContainsWildcardSyntaxInMiddle(path).invalidNel
    else path.valid
  }

  def validatePathsNonEmpty(resource: ResourceDetails): ValidationResult[ResourceDetails] =
    if (resource.paths.nonEmpty) resource.valid
    else NoPathsDefined.invalidNel

  def validateStackSetIntegration(externallyManagedServices: Boolean, definedServiceCount: Int): ValidationResult[Boolean] =
    externallyManagedServices match {
      case false if definedServiceCount == 0 => NoStackSetIntegrationAndNoServicesDefined.invalidNel
      case true if definedServiceCount > 0   => StacksetIntegrationDefinedWithServices.invalidNel
      case _                                 => externallyManagedServices.valid
    }

  def validateCorsUri(corsUriString: String): ValidationResult[String] = {
    if (corsUriString.contains("*")) {
      CorsDefinedWithWildcardAllowedOrigin.invalidNel
    } else {
      Try(parseCorsUri(corsUriString)) match {
        case Success(_) => corsUriString.valid
        case Failure(_) => CorsHostnamesInvalid(corsUriString).invalidNel
      }
    }
  }

  def validateCorsOptionsRoutes(corsConfig: Option[ValidationCorsConfig],
                                path: String,
                                verb: HttpVerb): ValidationResult[String] = {
    if (corsConfig.isDefined && verb == Options) {
      CorsDefinedWithOptionsRoute(path).invalidNel
    } else {
      s"$path:$verb".valid
    }
  }

  def validateNameLength(name: String, namespace: String): ValidationResult[String] = {
    if (name.length + namespace.length > 60)
      ResourceNameTooLong(name, namespace).invalidNel
    else s"$namespace:$name".valid
  }
}
