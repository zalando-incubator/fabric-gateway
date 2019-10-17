package ie.zalando.fabric.gateway.models

object ValidationDomain {

  case class ResourceDetails(paths: List[String])
  case class DecisionStatus(rejected: Boolean, reasons: List[RejectionReason] = Nil)
  case class RejectionReason(reason: String)
}
