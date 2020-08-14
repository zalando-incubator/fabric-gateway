package ie.zalando.fabric.gateway.service

import ie.zalando.fabric.gateway.models.SynchDomain.{GatewayMeta, GatewaySpec, HttpVerb, PathMatch, RateLimitPeriod}

object RouteDerivationModels {

  // Weights used to avoid collisions from conditional predicates added in Skipper
  sealed trait WeightedGatewayFeature {
    def weight: Int
  }

  object Admin extends WeightedGatewayFeature { val weight = 100 }
  object SvcRateLimit extends WeightedGatewayFeature { val weight = 80 }
  object SvcWhitelist extends WeightedGatewayFeature { val weight = 80 }
  object UserRateLimit extends WeightedGatewayFeature { val weight = 80 }
  object UserWhitelist extends WeightedGatewayFeature { val weight = 80 }
  object GlobalRateLimit extends WeightedGatewayFeature { val weight = 60 }
  object RejectNonWhitelisted extends WeightedGatewayFeature { val weight = 40 }
  object SvcMatch extends WeightedGatewayFeature { val weight = 40 }

  // Type used to for building a feature set and converting to a Skipper route type
  case class GatewayContext(gateway: GatewaySpec, meta: GatewayMeta)
  case class Route(path: PathMatch, verb: HttpVerb)

  case class RequiredScope(name: String)

  sealed trait ServiceIdentifier
  case object GenericServiceMatch       extends ServiceIdentifier
  case class NamedService(name: String) extends ServiceIdentifier

  case class ServiceRestrictionDetails(isRouteRestricted: Boolean, restrictedTo: Set[String])
  case class UserRestrictionDetails(restrictedTo: Set[String])
  case class RateLimitDetails(rate: Int, period: RateLimitPeriod)

  case class RouteConfig(
                          tokenValidations: Set[RequiredScope] = Set.empty,
                          adminAccessForRoute: Set[String] = Set.empty,
                          serviceRestrictions: ServiceRestrictionDetails = ServiceRestrictionDetails(isRouteRestricted = false, Set.empty),
                          userRestrictions: UserRestrictionDetails = UserRestrictionDetails(Set.empty),
                          rateLimitDetails: Map[ServiceIdentifier, RateLimitDetails] = Map.empty
                        )

  type FlowContext = (Route, RouteConfig, GatewayContext)

  type GatewayFeatureDerivation = FlowContext => FlowContext
}
