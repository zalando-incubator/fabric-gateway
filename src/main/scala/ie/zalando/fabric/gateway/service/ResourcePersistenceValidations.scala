package ie.zalando.fabric.gateway.service

import cats.data.Validated._
import cats.data._
import cats.implicits._
import ie.zalando.fabric.gateway.models.HttpModels.ValidationRequest
import ie.zalando.fabric.gateway.models.ValidationDomain.{DecisionStatus, RejectionReason, ResourceDetails}

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

  type ValidationResult[A] = ValidatedNel[GatewayValidation, A]

  def isValid(validationRequest: ValidationRequest): DecisionStatus =
    (
      validatePathsNonEmpty(validationRequest.resource),
      validationRequest.resource.paths.map(validateStarStarPathPosition).sequence,
      validateStackSetIntegration(validationRequest.hasExternallyManagedServices, validationRequest.definedServiceCount)
    ).mapN((_, _, _) => false) match {
      case Valid(notRejected) => DecisionStatus(notRejected)
      case Invalid(reasons) =>
        DecisionStatus(
          rejected = true,
          reasons = reasons.map(v => RejectionReason(v.errorMessage)).toList
        )
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
}
