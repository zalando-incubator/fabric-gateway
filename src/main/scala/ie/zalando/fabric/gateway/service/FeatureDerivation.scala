package ie.zalando.fabric.gateway.service

import ie.zalando.fabric.gateway.models.SynchDomain.{Disabled, Enabled, Inherited, Skipper}
import ie.zalando.fabric.gateway.service.RouteDerivationModels.{GatewayFeatureDerivation, GenericServiceMatch, NamedService, RateLimitDetails, RequiredScope, ServiceIdentifier, ServiceRestrictionDetails, UserRestrictionDetails}

object FeatureDerivation {

  val authentication: GatewayFeatureDerivation = {
    case (route, config, context) =>
      (route, config.copy(tokenValidations = config.tokenValidations + RequiredScope(Skipper.ZalandoTokenId)), context)
  }

  val authorization: GatewayFeatureDerivation = {
    case (route, config, context) =>
      val requiredScopes = context.gateway.paths
        .get(route.path)
        .flatMap { ctxtPathConf =>
          ctxtPathConf.operations.get(route.verb).flatMap { gwActions =>
            Some(
              gwActions.requiredPrivileges.toList
                .filterNot(_.toUpperCase == "UID")
                .map(RequiredScope)
                .toSet)
          }
        }
        .getOrElse(Set.empty)

      (route, config.copy(tokenValidations = config.tokenValidations ++ requiredScopes), context)
  }

  val adminAccess: GatewayFeatureDerivation = {
    case (route, config, context) =>
      (route, config.copy(adminAccessForRoute = context.gateway.admins), context)
  }

  val whitelisting: GatewayFeatureDerivation = {
    case (route, config, context) =>
      val globalWhitelistConfig = context.gateway.globalWhitelistConfig

      (for {
        pathConf <- context.gateway.paths.get(route.path)
        opConf   <- pathConf.operations.get(route.verb)
      } yield {
        (opConf.resourceWhitelistConfig.state, globalWhitelistConfig.state) match {
          case (Inherited, Inherited | Enabled) =>
            (route,
              config.copy(
                serviceRestrictions = ServiceRestrictionDetails(isRouteRestricted = true, globalWhitelistConfig.services)),
              context)
          case (Inherited, Disabled) | (Disabled, _) =>
            (route,
              config.copy(serviceRestrictions = ServiceRestrictionDetails(isRouteRestricted = false, Set.empty[String])),
              context)
          case (Enabled, _) =>
            (route,
              config.copy(serviceRestrictions =
                ServiceRestrictionDetails(isRouteRestricted = true, opConf.resourceWhitelistConfig.services)),
              context)
        }
      }) getOrElse ((route, config, context))
  }

  val employeeAccess: GatewayFeatureDerivation = {
    case (route, config, context) =>
      (for {
        pathConf             <- context.gateway.paths.get(route.path)
        opConf               <- pathConf.operations.get(route.verb)
        employeeAccessConfig = opConf.employeeAccessConfig
      } yield {
        (route, config.copy(userRestrictions = UserRestrictionDetails(employeeAccessConfig.employees)), context)
      }) getOrElse ((route, config, context))
  }

  val rateLimiting: GatewayFeatureDerivation = {
    case (route, config, context) =>
      (for {
        pathConf      <- context.gateway.paths.get(route.path)
        opConf        <- pathConf.operations.get(route.verb)
        rateLimitConf <- opConf.rateLimit
      } yield {
        val defaultRateLimits: Map[ServiceIdentifier, RateLimitDetails] =
          Map(GenericServiceMatch -> RateLimitDetails(rateLimitConf.defaultReqRate, rateLimitConf.period))
        val allRateLimits = rateLimitConf.uidSpecific.foldLeft(defaultRateLimits) {
          case (accum, (id, limit)) =>
            if (config.adminAccessForRoute.contains(id))
              accum
            else
              accum + (NamedService(id) -> RateLimitDetails(limit, rateLimitConf.period))
        }

        (route, config.copy(rateLimitDetails = allRateLimits), context)
      }) getOrElse ((route, config, context))
  }
}
