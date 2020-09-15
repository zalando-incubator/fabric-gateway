package ie.zalando.fabric.gateway.service

import ie.zalando.fabric.gateway.models.SynchDomain.{K8sServicePortIdentifier, NamedServicePort, NumericServicePort}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json._
import skuber._
import skuber.api.client.KubernetesClient
import skuber.api.client.K8SException

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class StackSetIdentifier(name: String, namespace: String)
case class StackSetIngress(backendPort: K8sServicePortIdentifier)
case class StackSetSpec(externalIngress: Option[StackSetIngress])
case class StackDefinedService(serviceName: String, servicePort: K8sServicePortIdentifier, weight: Double)
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
          e match {
            case notFound: K8SException if notFound.status.code.contains(404) =>
              log.info(s"Stackset does not exist, so generating no routes: $stackSet")
              Success(None)
            case _ =>
              log.warn(s"Call to K8s api failed to retrieve stack for $stackSet", e)
              Failure(new RuntimeException("Call to K8s api failed to retrieve stack for", e))
          }
      }
  }
}
