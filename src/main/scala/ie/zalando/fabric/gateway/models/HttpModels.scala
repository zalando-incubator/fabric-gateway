package ie.zalando.fabric.gateway.models

import ie.zalando.fabric.gateway.models.SynchDomain._
import ie.zalando.fabric.gateway.models.ValidationDomain.{RejectionReason, ResourceDetails, ValidationCorsConfig}

object HttpModels {

  val IngressKind       = "Ingress"
  val IngressApiVersion = "networking.k8s.io/v1"
  val DefaultNamespace  = "default"

  case class ControlledGatewayResource(status: Option[GatewayStatus], spec: GatewaySpec, metadata: GatewayMeta)
  case class SynchRequest(controlledResource: ControlledGatewayResource, currentState: NamedIngressDefinitions)
  case class SynchResponse(status: GatewayStatus, desiredIngressDefinitions: List[IngressDefinition])
  case class ValidationRequest(uid: String,
                               name: String,
                               namespace: String,
                               resource: ResourceDetails,
                               hasExternallyManagedServices: Boolean,
                               corsConfig: Option[ValidationCorsConfig],
                               definedServiceCount: Int)

  case class ValidationStatus(rejectionReasons: List[RejectionReason])
  case class ValidationResponse(uid: String, allowed: Boolean, result: Option[ValidationStatus])
  case class AdmissionReviewResponseWrapper(kind: String = "AdmissionReview",
                                            version: String = "admission.k8s.io/v1beta1",
                                            response: ValidationResponse)

}
