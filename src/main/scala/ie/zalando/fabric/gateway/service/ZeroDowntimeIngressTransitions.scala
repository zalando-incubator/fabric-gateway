package ie.zalando.fabric.gateway.service

import ie.zalando.fabric.gateway.models.SynchDomain.{GatewayMeta, GatewaySpec, IngressDefinition}

import scala.concurrent.{ExecutionContext, Future}

class ZeroDowntimeIngressTransitions(ingressDerivationChain: IngressDerivationChain) {

  def defineSafeRouteTransition(spec: GatewaySpec, metadata: GatewayMeta, existingRoutes: Seq[IngressDefinition])(
      implicit ec: ExecutionContext): Future[List[IngressDefinition]] = {
    ingressDerivationChain
      .deriveRoutesFor(spec, metadata)
      .map { desiredRoutes =>
        val updatedRouteNames = desiredRoutes.map(applyMigration)
        val deleted = diff(existingRoutes, updatedRouteNames)
        val created = diff(updatedRouteNames, existingRoutes)
        if (created.isEmpty) updatedRouteNames else updatedRouteNames ++ deleted
      }
  }

  private def diff(a: Seq[IngressDefinition], b: Seq[IngressDefinition]): Seq[IngressDefinition] =
    a.filterNot { i =>
      b.map(_.metadata.name).contains(i.metadata.name)
    }

  private def applyMigration(ingress: IngressDefinition): IngressDefinition = {
    if (!ingress.metadata.name.startsWith("m-")) ingress.copy(metadata = ingress.metadata.copy(name = s"m-${ingress.metadata.name}"))
    else ingress
  }
}
