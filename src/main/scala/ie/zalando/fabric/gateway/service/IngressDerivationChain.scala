package ie.zalando.fabric.gateway.service

import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import cats.data.{NonEmptyList => NEL}
import ie.zalando.fabric.gateway.models.SynchDomain._
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.immutable.Iterable
import scala.concurrent.{ExecutionContext, Future}

class IngressDerivationChain(stackSetOperations: StackSetOperations)(implicit mat: Materializer, ctxt: ExecutionContext) {

  private val log: Logger = LoggerFactory.getLogger(classOf[IngressDerivationChain])

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

  def deriveStateFrom(namedIngressDefinitions: NamedIngressDefinitions): GatewayStatus =
    GatewayStatus(namedIngressDefinitions.size, namedIngressDefinitions.keySet)

  def deriveRoutesFor(gateway: GatewaySpec, meta: GatewayMeta): Future[List[IngressDefinition]] = {
    val routes: Map[Route, RouteConfig] = for {
      (path, pathConf) <- gateway.paths
      (verb, _)        <- pathConf.operations
    } yield (Route(path, verb), RouteConfig())

    val defaultRoutes =
      SkipperRouteDefinition(
        meta.name.concat(DnsString.DefaultRouteSuffix),
        Nil,
        Nil,
        Some(
          SkipperCustomRoute(NEL.one(PathSubTreeMatch("/")),
                             NEL.of(RequiredPrivileges(NEL.one("uid")), Status(404), DefaultRejectMsg, Shunt)))
      ) :: SkipperRouteDefinition(
        meta.name.concat(DnsString.DefaultHttpRejectRouteSuffix),
        Nil,
        Nil,
        Some(SkipperCustomRoute(NEL.of(PathSubTreeMatch("/"), HttpTraffic), NEL.of(Status(400), HttpRejectMsg, Shunt)))
      ) :: Nil

    val routeDerivationOutput: Future[List[SkipperRouteDefinition]] = Source
      .fromIterator(() => routes.iterator)
      // Provide Global Context for each stage
      .map {
        case (route: Route, config: RouteConfig) =>
          (route, config, GatewayContext(gateway, meta))
      }
      .map(authentication)
      .map(authorization)
      .map(adminAccess)
      .map(whitelisting)
      .map(employeeAccess)
      .map(rateLimiting)
      .runWith(
        Sink.fold(defaultRoutes) {
          case (accum, (route, routeConfig, gatewayContext)) =>
            joinToSkipperRoutes(accum, route, routeConfig, gatewayContext)
        }
      )

    for {
      skipperRoutes <- routeDerivationOutput
      backends      <- serviceMappingsFromProvider(gateway.serviceProvider, meta.namespace)
    } yield {
      val finalRoutes = gateway.corsConfig.fold(skipperRoutes) { corsConfig =>
        withCors(gateway, meta.name, corsConfig, skipperRoutes)
      }
      combineBackendsAndRoutes(backends, finalRoutes, meta)
    }
  }

  def withCors(gateway: GatewaySpec,
               gatewayName: DnsString,
               corsConfig: CorsConfig,
               existingRoutes: List[SkipperRouteDefinition]): List[SkipperRouteDefinition] = {
    val preflightCorsRoutes: Iterable[SkipperRouteDefinition] = for {
      (path, pathConf) <- gateway.paths
      verbs            = pathConf.operations.keys.toSet
    } yield
      genCorsPreflightRoute(Route(path, Options),
                            gatewayName,
                            corsConfig.allowedOrigins,
                            corsConfig.allowedHeaders,
                            verbs + Options)

    val existingRoutesWithCors: List[SkipperRouteDefinition] = existingRoutes.map { route =>
      if (route.customRoute.isEmpty) {
        route.copy(filters = route.filters :+ CorsOrigin(corsConfig.allowedOrigins))
      } else {
        route
      }
    }

    existingRoutesWithCors ++ preflightCorsRoutes.toList
  }

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

  def joinToSkipperRoutes(skipperRoutes: List[SkipperRouteDefinition],
                          route: Route,
                          routeConfig: RouteConfig,
                          gatewayContext: GatewayContext): List[SkipperRouteDefinition] = {
    val adminRoutes = NEL
      .fromList(routeConfig.adminAccessForRoute.toList)
      .map { adminsNel =>
        genSkipperAdminsRoute(route, adminsNel, gatewayContext)
      }
      .toList

    val svcRoutes = if (routeConfig.serviceRestrictions.isRouteRestricted) {
      genRestrictedServiceRoutes(route, routeConfig, gatewayContext)
    } else {
      genUnrestrictedServiceRoutes(route, routeConfig, gatewayContext)
    }
    val genericRateLimit = routeConfig.rateLimitDetails.get(GenericServiceMatch)
    val employeeAccess = NEL.fromList(routeConfig.userRestrictions.restrictedTo.toList).flatMap { uids =>
      genericRateLimit
        .map { rl =>
          usersWhitelistedRateLimitedRoute(uids, rl, route, gatewayContext)
        }
        .orElse {
          Some(SkipperRouteDefinition(
            gatewayContext.meta.name.concat(DnsString.unlimitedUserPath(route.verb, route.path)),
            route.path :: MethodMatch(route.verb) :: UidMatch(uids) :: HttpsTraffic :: Nil,
            UidPrivilege ++ (FlowId :: ForwardTokenInfo :: Nil),
            None
          ))
        }
    }

    skipperRoutes ::: (adminRoutes ::: (svcRoutes ++ employeeAccess)).map { route =>
      if (route.filters.nonEmpty) {
        route.copy(filters = NonCustomerRealm :: route.filters)
      } else route
    }
  }

  def genCorsPreflightRoute(route: Route,
                            gatewayName: DnsString,
                            allowedOrigins: Set[Uri],
                            allowedHeaders: Set[String],
                            allowedMethods: Set[HttpVerb]): SkipperRouteDefinition = {
    val predicates = NEL.of(route.path, MethodMatch(route.verb), HttpsTraffic)
    val filters = NEL.of(
      EnableAccessLog(List(4, 5)),
      Status(204),
      FlowId,
      CorsOrigin(allowedOrigins),
      ResponseHeader("\"Access-Control-Allow-Methods\"", s""""${allowedMethods.map(_.value).mkString(", ")}""""),
      ResponseHeader("\"Access-Control-Allow-Headers\"", s""""${allowedHeaders.mkString(", ")}""""),
      Shunt
    )
    SkipperRouteDefinition(
      gatewayName.concat(DnsString.corsPath(route.verb, route.path)),
      List.empty,
      List.empty,
      Some(SkipperCustomRoute(predicates, filters))
    )
  }

  def genSkipperAdminsRoute(route: Route, admins: NEL[String], gatewayContext: GatewayContext): SkipperRouteDefinition = {
    val predicates = route.path :: MethodMatch(route.verb) :: UidMatch(admins) :: HttpsTraffic :: Nil
    val filters    = EnableAccessLog(List(2, 4, 5)) :: RequiredPrivileges(NEL.of(Skipper.ZalandoTokenId)) :: FlowId :: ForwardTokenInfo :: Nil

    SkipperRouteDefinition(gatewayContext.meta.name.concat(DnsString.userAdminPath(route.verb, route.path)),
                           predicates,
                           filters,
                           customRoute = None)
  }

  def genRestrictedServiceRoutes(route: Route,
                                 conf: RouteConfig,
                                 gatewayContext: GatewayContext): List[SkipperRouteDefinition] = {
    val svcSpecificRateLimits = conf.rateLimitDetails.collect {
      case (svcId @ NamedService(svcName), rl) if conf.serviceRestrictions.restrictedTo.contains(svcName) =>
        (svcId, rl)
    }

    val rateLimitedWhitelistedRoutes = svcSpecificRateLimits.foldLeft(List.empty[SkipperRouteDefinition]) {
      case (accum, (svcId, rl)) =>
        serviceSpecificRateLimitedRoute(svcId, rl, route, conf, gatewayContext) :: accum
    }

    // Generate service restricted routes with either default or no whitelisting
    val genericRateLimit  = conf.rateLimitDetails.get(GenericServiceMatch)
    val remainingServices = conf.serviceRestrictions.restrictedTo.diff(svcSpecificRateLimits.map(_._1.name).toSet)
    val remainingServiceRoutes = genericRateLimit
      .map { defaultRateLimit =>
        remainingServices.foldLeft(List.empty[SkipperRouteDefinition]) {
          case (accum, svc) =>
            serviceSpecificRateLimitedRoute(NamedService(svc), defaultRateLimit, route, conf, gatewayContext) :: accum
        }
      }
      .getOrElse {
        remainingServices.foldLeft(List.empty[SkipperRouteDefinition]) {
          case (accum, svc) =>
            SkipperRouteDefinition(
              gatewayContext.meta.name.concat(DnsString.unlimitedServicePath(route.verb, route.path, svc)),
              route.path :: MethodMatch(route.verb) :: ClientMatch(svc) :: HttpsTraffic :: Nil,
              requiredPrivileges(conf.tokenValidations).toList ++ (FlowId :: ForwardTokenInfo :: Nil),
              None
            ) :: accum
        }
      }

    // Generate 403 rejection routes for others
    SkipperRouteDefinition(
      gatewayContext.meta.name.concat(DnsString.nonWhitelistedReject(route.verb, route.path)),
      Nil,
      Nil,
      Some(
        SkipperCustomRoute(NEL.of(route.path, MethodMatch(route.verb), HttpsTraffic),
                           NEL.of(Status(403), UnauthorizedRejectMsg, Shunt)))
    ) :: remainingServiceRoutes ::: rateLimitedWhitelistedRoutes
  }

  def genUnrestrictedServiceRoutes(route: Route,
                                   conf: RouteConfig,
                                   gatewayContext: GatewayContext): List[SkipperRouteDefinition] = {
    if (conf.rateLimitDetails.isEmpty) {
      SkipperRouteDefinition(
        gatewayContext.meta.name.concat(DnsString.unlimitedPath(route.verb, route.path)),
        route.path :: MethodMatch(route.verb) :: HttpsTraffic :: Nil,
        requiredPrivileges(conf.tokenValidations).toList ++ (FlowId :: ForwardTokenInfo :: Nil),
        None
      ) :: Nil
    } else {
      conf.rateLimitDetails.foldLeft(List.empty[SkipperRouteDefinition]) {
        case (accum, (svcId, rl)) =>
          serviceSpecificRateLimitedRoute(svcId, rl, route, conf, gatewayContext) :: accum
      }
    }
  }

  def serviceSpecificRateLimitedRoute(svcId: ServiceIdentifier,
                                      rl: RateLimitDetails,
                                      route: Route,
                                      conf: RouteConfig,
                                      gatewayContext: GatewayContext): SkipperRouteDefinition = {
    svcId match {
      case GenericServiceMatch =>
        SkipperRouteDefinition(
          gatewayContext.meta.name.concat(DnsString.rateLimitedPath(route.verb, route.path)),
          route.path :: MethodMatch(route.verb) :: HttpsTraffic :: Nil,
          requiredPrivileges(conf.tokenValidations).toList ++ (GlobalRouteRateLimit(
            gatewayContext.meta.name.value,
            route.path,
            MethodMatch(route.verb),
            rl.rate,
            rl.period) :: FlowId :: ForwardTokenInfo :: Nil),
          None
        )
      case NamedService(svcName) =>
        SkipperRouteDefinition(
          gatewayContext.meta.name.concat(DnsString.rateLimitedServicePath(route.verb, route.path, svcName)),
          route.path :: MethodMatch(route.verb) :: ClientMatch(svcName) :: HttpsTraffic :: Nil,
          requiredPrivileges(conf.tokenValidations).toList ++ (ClientSpecificRouteRateLimit(
            gatewayContext.meta.name.value,
            route.path,
            MethodMatch(route.verb),
            ClientMatch(svcName),
            rl.rate,
            rl.period) :: FlowId :: ForwardTokenInfo :: Nil),
          None
        )
    }
  }

  def usersWhitelistedRateLimitedRoute(uids: NEL[String],
                                       rl: RateLimitDetails,
                                       route: Route,
                                       gatewayContext: GatewayContext): SkipperRouteDefinition = {
    SkipperRouteDefinition(
      gatewayContext.meta.name.concat(DnsString.rateLimitedUserPath(route.verb, route.path)),
      route.path :: MethodMatch(route.verb) :: UidMatch(uids) :: HttpsTraffic :: Nil,
      UidPrivilege ++ (GlobalUsersRouteRateLimit(gatewayContext.meta.name.value,
                                                 route.path,
                                                 MethodMatch(route.verb),
                                                 rl.rate,
                                                 rl.period) :: FlowId :: ForwardTokenInfo :: Nil),
      None
    )
  }

  def requiredPrivileges(scopes: Set[RequiredScope]): Option[RequiredPrivileges] = {
    NEL.fromList(scopes.map(_.name).toList).map { scopesNel =>
      RequiredPrivileges(scopesNel)
    }
  }

  def serviceMappingsFromProvider(provider: ServiceProvider, namespace: String): Future[Set[IngressBackend]] = provider match {
    case SchemaDefinedServices(svcMappings) => Future.successful(svcMappings)
    case StackSetProvidedServices(hosts, stackName) if hosts.nonEmpty =>
      stackSetOperations
        .getStatus(StackSetIdentifer(stackName, namespace))
        .map {
          case Some(status) =>
            status.traffic match {
              case Some(services) if services.nonEmpty =>
                hosts.map { host =>
                  IngressBackend(
                    host,
                    services
                      .map(stackSvcDesc =>
                        ServiceDescription(stackSvcDesc.serviceName, NumericServicePort(stackSvcDesc.servicePort), Some(stackSvcDesc.weight)))
                      .toSet)
                }
              case _ =>
                log.debug(s"No services defined in the status response for SS[$namespace:$stackName]")
                Set.empty[IngressBackend]
            }
          case None =>
            log.debug(s"No status object for for SS[$namespace:$stackName]")
            Set.empty[IngressBackend]
        }
    case StackSetProvidedServices(_, _) =>
      log.debug("No hosts are defined for this gateway, cannot create ingressii")
      Future.successful(Set.empty[IngressBackend])
  }

  def combineBackendsAndRoutes(backends: Set[IngressBackend],
                               routes: List[SkipperRouteDefinition],
                               meta: GatewayMeta): List[IngressDefinition] = {
    val creatableRoutes = if (backends.isEmpty) Nil else routes

    creatableRoutes.map { skipperRoute =>
      val serviceWeights: Set[(String, Int)] = backends
        .flatMap(
          _.services
            .map(svc => (svc.name, svc.trafficWeight))
            .collect {
              case (svcName, Some(weight)) => (svcName, weight)
            })

      val annotatedRoute = {
        val weightAnnotatedRoute = if (serviceWeights.nonEmpty) {
          skipperRoute.copy(
            additionalAnnotations = skipperRoute.additionalAnnotations ++ createBackendWeightsAnnotation(serviceWeights))
        } else skipperRoute

        // Use Old TLS versions to aid migration (MID-2126)
        val tlsAnnotatedRoute =
          weightAnnotatedRoute.copy(additionalAnnotations = weightAnnotatedRoute.additionalAnnotations ++ useTlsV1_1Annotation)

        tlsAnnotatedRoute
      }

      IngressDefinition(
        backends,
        IngressMetaData(
          annotatedRoute,
          annotatedRoute.name.value,
          meta.namespace
        )
      )
    }
  }

  def createBackendWeightsAnnotation(serviceWeights: Set[(String, Int)]): Map[String, String] = {
    if (serviceWeights.isEmpty) Map.empty[String, String]
    else
      Map(
        "zalando.org/backend-weights" -> serviceWeights
          .map {
            case (svcName, weight) =>
              s""""$svcName":$weight"""
          }
          .mkString("{", ",", "}"))
  }

  val useTlsV1_1Annotation: Map[String, String] = Map(
    "zalando.org/aws-load-balancer-ssl-policy" -> "ELBSecurityPolicy-FS-2018-06")
  val UidPrivilege: List[RequiredPrivileges] = requiredPrivileges(Set(RequiredScope("uid"))).toList
}
