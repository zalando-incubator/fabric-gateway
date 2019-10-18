package ie.zalando.fabric.gateway.models

import ie.zalando.fabric.gateway.models.SynchDomain.{CorsState, HttpVerb}

object ValidationDomain {

  case class ResourceDetails(paths: Map[String, Map[HttpVerb, Any]])
  case class DecisionStatus(rejected: Boolean, reasons: List[RejectionReason] = Nil)
  case class RejectionReason(reason: String)
  case class ValidationCorsConfig(state: CorsState, allowedOrigins: Set[String])
}
