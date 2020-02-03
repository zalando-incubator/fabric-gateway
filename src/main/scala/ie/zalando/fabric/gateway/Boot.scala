package ie.zalando.fabric.gateway

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteConcatenation._
import akka.stream.ActorMaterializer
import ie.zalando.fabric.gateway.features.{TlsEndpointSupport, VersionedHostsEnabled}
import ie.zalando.fabric.gateway.service.{IngressDerivationChain, StackSetOperations}
import ie.zalando.fabric.gateway.web.{GatewayWebhookRoutes, HttpsContext, OperationalRoutes}
import skuber.k8sInit

import scala.concurrent.ExecutionContext

object Boot extends App with GatewayWebhookRoutes with OperationalRoutes with HttpsContext {

  implicit val system: ActorSystem             = ActorSystem("Fabric-Gateway-Operator")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val dispatcher: ExecutionContext    = system.dispatcher

  val versionedHostsBaseDomain = VersionedHostsEnabled.runIfEnabled { () =>
    sys.env.get(VersionedHostsEnabled.baseDomainEnvName).map(_.toLowerCase.trim)
      .getOrElse {
        throw new IllegalStateException(s"ENV Var '${VersionedHostsEnabled.baseDomainEnvName}' needs to be set if '${VersionedHostsEnabled.envName}' is enabled")
      }
  }

  val k8s            = k8sInit
  val ssOps          = new StackSetOperations(k8s)
  val ingDerivations = new IngressDerivationChain(ssOps, versionedHostsBaseDomain)

  lazy val routes: Route = createRoutesFromDerivations(ingDerivations) ~ operationalRoutes

  Http().bindAndHandle(routes, "0.0.0.0", 8080)

  TlsEndpointSupport.runIfEnabled { () =>
    system.log.info("TLS endpoint is enabled")
    Http().bindAndHandle(routes, "0.0.0.0", 8443, connectionContext = httpsCtxt)
  }

  system.log.info("Now accepting requests for fabric gateway operator")
}
