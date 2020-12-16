package ie.zalando.fabric.gateway.service

import ie.zalando.fabric.gateway.models.SynchDomain.{GatewayMeta, GatewaySpec, IngressDefinition}

import scala.concurrent.{ExecutionContext, Future}

class ZeroDowntimeIngressTransitions(ingressDerivationChain: IngressDerivationChain) {

  def defineSafeRouteTransition(spec: GatewaySpec, metadata: GatewayMeta, existingRoutes: Seq[IngressDefinition])(
      implicit ec: ExecutionContext): Future[List[IngressDefinition]] = {
    ingressDerivationChain
      .deriveRoutesFor(spec, metadata)
      .map { desiredRoutes =>
        // Temporarily changing names for new Routes. This code should be removed when all routes are switched to new apiVersion
        val migrationNamedDesiredRoutes: List[IngressDefinition] = desiredRoutes.map(id => {
          if (id.metadata.name.startsWith("m_")) id
          else id.copy(metadata = id.metadata.copy(name = s"m_${id.metadata.name}"))
        })

        val deleted = diff(existingRoutes, migrationNamedDesiredRoutes)
        val created = diff(migrationNamedDesiredRoutes, existingRoutes)
        if (created.isEmpty) migrationNamedDesiredRoutes else migrationNamedDesiredRoutes ++ deleted
      }
  }

  private def diff(a: Seq[IngressDefinition], b: Seq[IngressDefinition]) =
    a.filterNot { i =>
      b.map(_.metadata.name).contains(i.metadata.name)
    }
}
