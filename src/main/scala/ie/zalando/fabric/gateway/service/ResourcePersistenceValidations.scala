package ie.zalando.fabric.gateway.service

import akka.http.scaladsl.model.Uri
import cats.data.Validated._
import cats.data._
import cats.implicits._
import ie.zalando.fabric.gateway.models.HttpModels.ValidationRequest
import ie.zalando.fabric.gateway.models.SynchDomain.{HttpVerb, Options}
import ie.zalando.fabric.gateway.models.ValidationDomain.{DecisionStatus, RejectionReason, ResourceDetails, ValidationCorsConfig}

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
      "You must have at least 1 `x-fabric-service` defined, or mark the gateway as `x-service-definition: stackset`"
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

  type ValidationResult[A] = ValidatedNel[GatewayValidation, A]

  def isValid(validationRequest: ValidationRequest): DecisionStatus = {
    val pathVerbPairs: List[(String, HttpVerb)] = validationRequest.resource.paths.toList.flatMap {
      case (path, ops) => ops.map { case (verb, _) => (path, verb) }
    }
    (
      validatePathsNonEmpty(validationRequest.resource),
      validationRequest.resource.paths.keys.toList.map(validateStarStarPathPosition).sequence,
      validateStackSetIntegration(validationRequest.hasExternallyManagedServices, validationRequest.definedServiceCount),
      validationRequest.corsConfig
        .map(_.allowedOrigins.toList.map(hostname => validateCorsHostname(hostname)))
        .getOrElse(List.empty)
        .sequence,
      pathVerbPairs.map {
        case (path, verb) => validateCorsOptionsRoutes(validationRequest.corsConfig, path, verb)
      }.sequence
    ).mapN((_, _, _, _, _) => false) match {
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

  def validateCorsHostname(hostname: String): ValidationResult[String] = {
    if (hostname.contains("*")) {
      CorsDefinedWithWildcardAllowedOrigin.invalidNel
    } else {
      Try(Uri.from(host = hostname)) match {
        case Success(_) => hostname.valid
        case Failure(_) => CorsHostnamesInvalid(hostname).invalidNel
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
}
