package ie.zalando.fabric.gateway.service

import ie.zalando.fabric.gateway.models.SynchDomain.{K8sServicePortIdentifier, NamedServicePort, NumericServicePort}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import skuber._
import skuber.api.client.KubernetesClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class StackSetIdentifier(name: String, namespace: String)
case class StackSetIngress(backendPort: Int)
case class StackSetSpec(externalIngress: Option[StackSetIngress])
case class StackDefinedService(serviceName: String, servicePort: K8sServicePortIdentifier, weight: Int)
case class StackSetStatus(readyStacks: Int, stacks: Int, stacksWithTraffic: Int, traffic: Option[Seq[StackDefinedService]])

class StackSetOperations(k8sClient: KubernetesClient)(implicit execCtxt: ExecutionContext) {

  private val log: Logger = LoggerFactory.getLogger(classOf[StackSetOperations])

  private type StackSet = CustomResource[StackSetSpec, StackSetStatus]

  implicit val servicePortReads: Reads[K8sServicePortIdentifier] = {
    case JsString(s) => JsSuccess(NamedServicePort(s))
    case other => Reads.IntReads.reads(other).map(NumericServicePort)
  }
  implicit val servicePortWrites: Writes[K8sServicePortIdentifier] = {
    case NamedServicePort(name) => JsString(name)
    case NumericServicePort(port) => JsNumber(port)
  }
  implicit val stackSvcReads: Reads[StackDefinedService] = (
    (JsPath \ "serviceName").read[String] and
      (JsPath \ "servicePort").read[K8sServicePortIdentifier] and
      (JsPath \ "weight").read[Int]
    ) (StackDefinedService.apply _)
  
  implicit val stackSvcWrites: Writes[StackDefinedService] = (
    (JsPath \ "serviceName").write[String] and
      (JsPath \ "servicePort").write[K8sServicePortIdentifier] and
      (JsPath \ "weight").write[Int]
    ) (unlift(StackDefinedService.unapply))

  implicit val stackSvcFmt: Format[StackDefinedService] = Json.format[StackDefinedService]
  implicit val stackIngressFmt: Format[StackSetIngress] = Json.format[StackSetIngress]
  implicit val specFmt: Format[StackSetSpec]            = Json.format[StackSetSpec]
  implicit val statusFmt: Format[StackSetStatus]        = Json.format[StackSetStatus]

  implicit val testResourceDefinition: ResourceDefinition[StackSet] = ResourceDefinition[StackSet](
    kind = "StackSet",
    group = "zalando.org",
    version = "v1"
  )

  def getStatus(stackSet: StackSetIdentifier): Future[Option[StackSetStatus]] = {
    k8sClient
      .getInNamespace[StackSet](stackSet.name, stackSet.namespace)
      .map(_.status)
      .transform {
        case Success(maybeStatus) => Success(maybeStatus)
        case Failure(e) =>
          log.warn(s"Call to K8s api failed to retrieve stack for $stackSet", e)
          Success(None)
      }
  }
}
