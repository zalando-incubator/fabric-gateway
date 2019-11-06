package ie.zalando.fabric.gateway

import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import ie.zalando.fabric.gateway.TestJsonModels._
import ie.zalando.fabric.gateway.models.SynchDomain.K8sServicePortIdentifier
import ie.zalando.fabric.gateway.web.marshalling.JsonModels
import io.circe.{Decoder, HCursor, Json}

object TestJsonModels {

  case class TestableIngressBackend(serviceName: String, servicePort: K8sServicePortIdentifier)
  case class IngressRule(host: String, paths: Seq[TestableIngressBackend])
  case class TestableIngress(name: String,
                             namespace: String,
                             rules: Seq[IngressRule],
                             route: Option[String],
                             predicates: Option[String],
                             filters: Option[String],
                             allAnnos: Map[String, Json])
  case class TestSynchResponse(ingressii: List[TestableIngress])

  case class TestValidationResponseStatus(reason: String)
  case class TestValidationResponse(uid: String, allowed: Boolean, status: Option[TestValidationResponseStatus])
}

trait TestJsonModels extends JsonModels with FailFastCirceSupport {

  implicit val decodeEgressIngressBackend: Decoder[TestableIngressBackend] = (c: HCursor) =>
    for {
      name <- c.downField("backend").downField("serviceName").as[String]
      port <- c.downField("backend").downField("servicePort").as[K8sServicePortIdentifier]
    } yield TestableIngressBackend(name, port)

  implicit val decodeIngressRule: Decoder[IngressRule] = (c: HCursor) =>
    for {
      host  <- c.downField("host").as[String]
      paths <- c.downField("http").downField("paths").as[Seq[TestableIngressBackend]]
    } yield IngressRule(host, paths)

  implicit val decodeIngressDefinition: Decoder[TestableIngress] = (c: HCursor) =>
    for {
      name      <- c.downField("metadata").downField("name").as[String]
      namespace <- c.downField("metadata").downField("namespace").as[String]
      rules     <- c.downField("spec").downField("rules").as[Seq[IngressRule]]
      maybeCustomRoute <- c.downField("metadata")
                           .downField("annotations")
                           .downField("zalando.org/skipper-routes")
                           .as[Option[String]]
      maybePredicates <- c.downField("metadata")
                          .downField("annotations")
                          .downField("zalando.org/skipper-predicate")
                          .as[Option[String]]
      maybeFilters <- c.downField("metadata").downField("annotations").downField("zalando.org/skipper-filter").as[Option[String]]
      allAnnos     <- c.downField("metadata").downField("annotations").as[Map[String, Json]]
    } yield TestableIngress(name, namespace, rules, maybeCustomRoute, maybePredicates, maybeFilters, allAnnos)

  implicit val decodeSynchResponse: Decoder[TestSynchResponse] = (c: HCursor) =>
    for {
      ingressii <- c.downField("children").as[List[TestableIngress]]
    } yield TestSynchResponse(ingressii)

  implicit val decodeValidationResponseStatus: Decoder[TestValidationResponseStatus] = (c: HCursor) =>
    for {
      reasons <- c.downField("reason").as[String]
    } yield TestValidationResponseStatus(reasons)

  implicit val decodeValidationResponse: Decoder[TestValidationResponse] = (c: HCursor) =>
    for {
      uid    <- c.downField("response").downField("uid").as[String]
      allow  <- c.downField("response").downField("allowed").as[Boolean]
      status <- c.downField("response").downField("status").as[Option[TestValidationResponseStatus]]
    } yield TestValidationResponse(uid, allow, status)
}
