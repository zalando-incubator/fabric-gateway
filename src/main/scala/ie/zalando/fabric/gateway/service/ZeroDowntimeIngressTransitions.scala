package ie.zalando.fabric.gateway.service

import ie.zalando.fabric.gateway.models.SynchDomain.{GatewayMeta, GatewaySpec, IngressDefinition}

import scala.concurrent.{ExecutionContext, Future}

class ZeroDowntimeIngressTransitions(ingressDerivationChain: IngressDerivationChain) {

  def defineSafeRouteTransition(spec: GatewaySpec, metadata: GatewayMeta, existingRoutes: Seq[IngressDefinition])(
      implicit ec: ExecutionContext): Future[List[IngressDefinition]] = {
    ingressDerivationChain
      .deriveRoutesFor(spec, metadata)
      .map { desiredRoutes =>
        val deleted = relativeComplement(desiredRoutes, existingRoutes)
        val created = relativeComplement(existingRoutes, desiredRoutes)
        if (created.isEmpty) desiredRoutes else desiredRoutes ++ deleted
      }
  }

  private def relativeComplement(a: Seq[IngressDefinition], b: Seq[IngressDefinition]) =
    b.filterNot { i =>
      a.map(_.metadata.name).contains(i.metadata.name)
    }
}
