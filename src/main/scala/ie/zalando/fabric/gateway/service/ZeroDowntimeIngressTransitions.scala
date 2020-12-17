package ie.zalando.fabric.gateway.service

import ie.zalando.fabric.gateway.models.HttpModels
import ie.zalando.fabric.gateway.models.SynchDomain.{GatewayMeta, GatewaySpec, IngressDefinition}

import scala.concurrent.{ExecutionContext, Future}

class ZeroDowntimeIngressTransitions(ingressDerivationChain: IngressDerivationChain) {

  def defineSafeRouteTransition(spec: GatewaySpec, metadata: GatewayMeta, existingRoutes: Seq[IngressDefinition], isLegacy: Boolean)(
      implicit ec: ExecutionContext): Future[List[IngressDefinition]] = {
    ingressDerivationChain
      .deriveRoutesFor(spec, metadata)
      .map { desiredRoutes =>
        val deleted = diff(existingRoutes, desiredRoutes)
        val created = diff(desiredRoutes, existingRoutes)
        (if (created.isEmpty) desiredRoutes else desiredRoutes ++ deleted).map(r => applyMigration(r, isLegacy))
      }
  }

  private def diff(a: Seq[IngressDefinition], b: Seq[IngressDefinition]): Seq[IngressDefinition] =
    a.filterNot { i =>
      b.map(_.metadata.name).contains(i.metadata.name)
    }

  private def applyMigration(ingress: IngressDefinition, isLegacy: Boolean): IngressDefinition =
    if (isLegacy) ingress.copy(apiVersion = HttpModels.LegacyIngressApiVersion)
    else ingress.copy(metadata = ingress.metadata.copy(name = s"m-${ingress.metadata.name}"))
}
