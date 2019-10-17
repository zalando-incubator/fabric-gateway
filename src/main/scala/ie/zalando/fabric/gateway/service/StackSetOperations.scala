package ie.zalando.fabric.gateway.service

import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json._
import skuber._
import skuber.api.client.KubernetesClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class StackSetIdentifer(name: String, namespace: String)
case class StackSetIngress(backendPort: String, hosts: Seq[String])
case class StackSetSpec(ingress: StackSetIngress)
case class StackDefinedService(serviceName: String, servicePort: String, weight: Int)
case class StackSetStatus(readyStacks: Int, stacks: Int, stacksWithTraffic: Int, traffic: Option[Seq[StackDefinedService]])

class StackSetOperations(k8sClient: KubernetesClient)(implicit execCtxt: ExecutionContext) {

  private val log: Logger = LoggerFactory.getLogger(classOf[StackSetOperations])

  private type StackSet = CustomResource[StackSetSpec, StackSetStatus]

  implicit val stackSvcFmt: Format[StackDefinedService] = Json.format[StackDefinedService]
  implicit val stackIngressFmt: Format[StackSetIngress] = Json.format[StackSetIngress]
  implicit val specFmt: Format[StackSetSpec]            = Json.format[StackSetSpec]
  implicit val statusFmt: Format[StackSetStatus]        = Json.format[StackSetStatus]

  implicit val testResourceDefinition: ResourceDefinition[StackSet] = ResourceDefinition[StackSet](
    kind = "StackSet",
    group = "zalando.org",
    version = "v1"
  )

  def getStatus(stackSet: StackSetIdentifer): Future[Option[StackSetStatus]] = {
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
