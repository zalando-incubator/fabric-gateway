package ie.zalando.fabric.gateway.service

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.data.{NonEmptyList => NEL}
import ie.zalando.fabric.gateway.models.SynchDomain._
import ie.zalando.fabric.gateway.web.marshalling.JsonModels
import org.mockito.scalatest.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FlatSpec, Matchers}
import skuber.api.client.KubernetesClient

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class IngressDerivationChainSpec extends FlatSpec with MockitoSugar with Matchers with JsonModels {

  implicit val ec: ExecutionContext   = ExecutionContext.global
  implicit val as: ActorSystem        = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()

  val kubernetesClient       = mock[KubernetesClient]
  val stackSetOperations     = new StackSetOperations(kubernetesClient)
  val ingressDerivationLogic = new IngressDerivationChain(stackSetOperations)

  val AdminUser                 = "adminUser"
  val WhitelistedUser           = "whitelistedUser"
  val ResourceWhitelistedUser   = "resourceWhitelistedUser"
  val InheritedWhitelistDetails = WhitelistConfig(Set(), Inherited)
  val UserWhitelist             = EmployeeAccessConfig(Set.empty)

  val sampleGateway = GatewaySpec(
    SchemaDefinedServices(Set(IngressBackend("host", Set(ServiceDescription("svc"))))),
    Set(AdminUser),
    WhitelistConfig(Set(), Disabled),
    Map(
      PathMatch("/api/resource") -> PathConfig(
        Map(
          Get -> ActionAuthorizations(
            NEL.of("uid", "service.read"),
            Some(RateLimitDetails(10, PerMinute, Map.empty[String, Int])),
            InheritedWhitelistDetails,
            UserWhitelist
          ),
          Post -> ActionAuthorizations(
            NEL.of("uid", "service.write"),
            Some(
              RateLimitDetails(10,
                               PerMinute,
                               Map(
                                 AdminUser   -> 25,
                                 "otherUser" -> 35
                               ))),
            InheritedWhitelistDetails,
            UserWhitelist
          )
        )),
      PathMatch("/api/resource/*") -> PathConfig(
        Map(
          Get -> ActionAuthorizations(
            NEL.of("uid", "service.read"),
            None,
            InheritedWhitelistDetails,
            UserWhitelist
          )
        ))
    )
  )

  val sampleGloballyWhitelistedGateway = GatewaySpec(
    SchemaDefinedServices(Set(IngressBackend("host", Set(ServiceDescription("svc", "named"))))),
    Set(AdminUser),
    WhitelistConfig(Set(WhitelistedUser), Enabled),
    Map(
      PathMatch("/api/resource") -> PathConfig(
        Map(
          Get -> ActionAuthorizations(
            NEL.of("uid", "service.read"),
            Some(RateLimitDetails(10, PerMinute, Map.empty[String, Int])),
            InheritedWhitelistDetails,
            UserWhitelist
          ),
          Post -> ActionAuthorizations(
            NEL.of("uid", "service.write"),
            Some(
              RateLimitDetails(10,
                               PerMinute,
                               Map(
                                 AdminUser       -> 25,
                                 WhitelistedUser -> 25,
                                 "otherUser"     -> 35
                               ))),
            InheritedWhitelistDetails,
            UserWhitelist
          )
        )),
      PathMatch("/api/resource/*") -> PathConfig(
        Map(
          Get -> ActionAuthorizations(
            NEL.of("uid", "service.read"),
            None,
            InheritedWhitelistDetails,
            UserWhitelist
          ))
      )
    )
  )

  val sampleResourceWhitelistingGateway = GatewaySpec(
    SchemaDefinedServices(Set(IngressBackend("host", Set(ServiceDescription("svc", "named"))))),
    Set(AdminUser),
    WhitelistConfig(Set(WhitelistedUser), Enabled),
    Map(
      PathMatch("/api/resource") -> PathConfig(
        Map(
          Get -> ActionAuthorizations(
            NEL.of("uid", "service.read"),
            Some(RateLimitDetails(10, PerMinute, Map.empty[String, Int])),
            InheritedWhitelistDetails,
            UserWhitelist
          ),
          Post -> ActionAuthorizations(
            NEL.of("uid", "service.write"),
            Some(
              RateLimitDetails(10,
                               PerMinute,
                               Map(
                                 AdminUser       -> 25,
                                 WhitelistedUser -> 25,
                                 "otherUser"     -> 35
                               ))),
            WhitelistConfig(Set(ResourceWhitelistedUser), Enabled),
            UserWhitelist
          )
        )),
      PathMatch("/api/resource/*") -> PathConfig(
        Map(
          Get -> ActionAuthorizations(
            NEL.of("uid", "service.read"),
            None,
            WhitelistConfig(Set(), Disabled),
            UserWhitelist
          )
        ))
    )
  )

  val sampleUserWhitelistingGateway = GatewaySpec(
    SchemaDefinedServices(Set(IngressBackend("host", Set(ServiceDescription("svc", "named"))))),
    Set(AdminUser),
    WhitelistConfig(Set(WhitelistedUser), Enabled),
    Map(
      PathMatch("/api/resource") -> PathConfig(
        Map(
          Get -> ActionAuthorizations(
            NEL.of("uid", "service.read"),
            None,
            InheritedWhitelistDetails,
            EmployeeAccessConfig(Set(WhitelistedUser))
          ),
          Post -> ActionAuthorizations(
            NEL.of("uid", "service.write"),
            None,
            WhitelistConfig(Set(ResourceWhitelistedUser), Enabled),
            UserWhitelist
          )
        )),
      PathMatch("/api/resource/*") -> PathConfig(
        Map(
          Get -> ActionAuthorizations(
            NEL.of("uid", "service.read"),
            Some(RateLimitDetails(10, PerMinute, Map.empty[String, Int])),
            WhitelistConfig(Set(), Disabled),
            EmployeeAccessConfig(Set(WhitelistedUser))
          )
        ))
    )
  )

  val testableRouteDerivationWithCatchAll: List[IngressDefinition] =
    Await.result(ingressDerivationLogic.deriveRoutesFor(sampleGateway,
                                                        GatewayMeta(DnsString.fromString("gateway-name").get, "my-namespace")),
                 10.seconds)

  def testableWhitelistRoutesDerivation(gw: GatewaySpec): List[IngressDefinition] =
    Await.result(
      ingressDerivationLogic.deriveRoutesFor(gw, GatewayMeta(DnsString.fromString("whitelisted-gateway").get, "my-namespace")),
      10.seconds)

  val testableRouteDerivation: List[IngressDefinition] = testableRouteDerivationWithCatchAll.drop(2)

  "Namespacing" should "include the correct namespace in each ingress" in {
    testableRouteDerivation.foreach(_.metadata.namespace shouldBe "my-namespace")
  }

  "Admin Routes" should "not be generated if there are no admin users" in {
    val routes = Await
      .result(
        ingressDerivationLogic.deriveRoutesFor(sampleGateway.copy(admins = Set.empty[String]),
                                               GatewayMeta(DnsString.fromString("gateway-name").get, "my-namespace")),
        10.seconds
      )
      .filter(isAdminRoute)

    routes.size shouldBe 0
  }

  it should "enable the access log" in {
    val routes = Await
      .result(
        ingressDerivationLogic.deriveRoutesFor(sampleGateway,
                                               GatewayMeta(DnsString.fromString("gateway-name").get, "my-namespace")),
        10.seconds
      )
      .filter(isAdminRoute)

    routes.size should not be 0
    routes.forall(_.metadata.routeDefinition.filters.contains(EnableAccessLog(List(2, 4, 5)))) shouldBe true
  }

  "CatchAll 404 route" should "be the first route in the list and a custom skipper route" in {
    val defaultRoute = Await
      .result(
        ingressDerivationLogic.deriveRoutesFor(sampleGateway,
                                               GatewayMeta(DnsString.fromString("gateway-name").get, "my-namespace")),
        10.seconds
      )
      .head
      .metadata
      .routeDefinition

    defaultRoute.predicates shouldBe empty
    defaultRoute.filters shouldBe empty
    val Some(route) = defaultRoute.customRoute
    route.predicates.toList should contain(PathSubTreeMatch("/"))
    route.filters.toList should contain allOf (Status(404), DefaultRejectMsg, Shunt)
  }

  it should "generate a valid skipper admin route per verb" in {
    val routes = Await
      .result(
        ingressDerivationLogic.deriveRoutesFor(sampleGateway,
                                               GatewayMeta(DnsString.fromString("gateway-name").get, "my-namespace")),
        10.seconds
      )
      .filter(isAdminRoute)
      .map(_.metadata.routeDefinition)

    routes.count(sr => sr.customRoute.nonEmpty) should be(0)
    routes.size shouldBe 3

    routes.foreach { sr =>
      sr.predicates.size should be(4)
      sr.predicates.exists { _.getClass == classOf[PathMatch] } should be(true)
      sr.predicates.exists { _.getClass == classOf[MethodMatch] } should be(true)
      sr.predicates.exists { _.getClass == classOf[UidMatch] } should be(true)
      sr.filters should equal(
        List(NonCustomerRealm, EnableAccessLog(List(2, 4, 5)), RequiredPrivileges(NEL.of("uid")), FlowId, ForwardTokenInfo))
    }

    routes
      .map(_.predicates)
      .contains(List(PathMatch("/api/resource"), MethodMatch(Get), UidMatch(NEL.one(AdminUser)), HttpsTraffic)) shouldBe true
    routes
      .map(_.predicates)
      .contains(List(PathMatch("/api/resource"), MethodMatch(Post), UidMatch(NEL.one(AdminUser)), HttpsTraffic)) shouldBe true
    routes
      .map(_.predicates)
      .contains(List(PathMatch("/api/resource/*"), MethodMatch(Get), UidMatch(NEL.one(AdminUser)), HttpsTraffic)) shouldBe true
  }

  "Route Filtering" should "not generate rate limits for admins" in {
    val gatewayPaths: GatewayPaths = Map(
      PathMatch("/api/resource") -> PathConfig(
        Map(
          Get -> ActionAuthorizations(NEL.of("uid"),
                                      Some(RateLimitDetails(10, PerMinute, Map("a" -> 20, "b" -> 25, "c" -> 30))),
                                      InheritedWhitelistDetails,
                                      UserWhitelist),
          Post -> ActionAuthorizations(NEL.of("uid"),
                                       Some(RateLimitDetails(2, PerMinute, Map("a" -> 10, "c" -> 10))),
                                       InheritedWhitelistDetails,
                                       UserWhitelist)
        ))
    )
    val gwSpec = GatewaySpec(
      SchemaDefinedServices(Set(IngressBackend("host", Set(ServiceDescription("svcName"))))),
      Set.empty[String],
      WhitelistConfig(Set.empty[String], Disabled),
      gatewayPaths
    )

    val filters = Table(
      ("admins", "expected"),
      (Set.empty[String], Set("a", "b", "c")),
      (Set("a"), Set("b", "c")),
      (Set("d", "e", "f"), Set("a", "b", "c"))
    )

    forAll(filters) { (admins: Set[String], rateLimitedServices: Set[String]) =>
      val routes = Await.result(
        ingressDerivationLogic.deriveRoutesFor(gwSpec.copy(admins = admins), GatewayMeta(DnsString.apply("my-test-gw"), "ns")),
        10.seconds
      )

      val svcRestrictedLimits = routes
        .flatMap(_.metadata.routeDefinition.filters)
        .collect {
          case ClientSpecificRouteRateLimit(_, _, _, ClientMatch(svcName), _, _) => svcName
        }
        .toSet

      svcRestrictedLimits shouldBe rateLimitedServices
    }
  }

  it should "restrict rate limits to only whitelisted services if any present" in {
    val gatewayPaths: GatewayPaths = Map(
      PathMatch("/api/resource") -> PathConfig(
        Map(
          Get -> ActionAuthorizations(NEL.of("uid"),
                                      Some(RateLimitDetails(10, PerMinute, Map("a" -> 20, "b" -> 25, "c" -> 30))),
                                      InheritedWhitelistDetails,
                                      UserWhitelist),
          Post -> ActionAuthorizations(NEL.of("uid"),
                                       Some(RateLimitDetails(2, PerMinute, Map("a" -> 10, "c" -> 10))),
                                       InheritedWhitelistDetails,
                                       UserWhitelist)
        ))
    )
    val gwSpec = GatewaySpec(
      SchemaDefinedServices(Set(IngressBackend("host", Set(ServiceDescription("svcName"))))),
      Set.empty[String],
      WhitelistConfig(Set("a"), Enabled),
      gatewayPaths
    )

    val routes = Await.result(
      ingressDerivationLogic.deriveRoutesFor(gwSpec, GatewayMeta(DnsString.apply("my-test-gw"), "ns")),
      10.seconds
    )

    val svcRestrictedLimits = routes
      .flatMap(_.metadata.routeDefinition.filters)
      .collect {
        case ClientSpecificRouteRateLimit(_, _, _, ClientMatch(svcName), _, _) => svcName
      }
      .toSet

    svcRestrictedLimits shouldBe Set("a")
  }

  "Route derivation" should "generate a list of Skipper Routes" in {
    testableRouteDerivation.size shouldBe 7
  }

  it should "generate a distinct name for each route" in {
    val routeNames = testableRouteDerivation.map(_.metadata.name)
    routeNames.toSet.size shouldBe routeNames.size
  }

  it should "generate a whitelisted route per user for each action" in {
    testableRouteDerivation.count(isAdminRoute) shouldBe 3
  }

  it should "exclude blacklisted users from the whitelist" in {
    testableRouteDerivation.filter(isAdminRoute).map(_.metadata).flatMap(_.routeDefinition.predicates).count {
      case pred: UidMatch if pred.uids.exists(_ == "d") => true
      case _                                            => false
    } shouldBe 0
  }

  it should "generate the correct routes for non black or whitelisted access" in {
    val baseRoutes = testableRouteDerivation
      .filterNot(isAdminRoute)

    baseRoutes
      .map(_.metadata)
      .flatMap(_.routeDefinition.predicates)
      .count {
        case p: PathMatch if p.path.startsWith("/api/resource") => true
        case _                                                  => false
      } shouldBe baseRoutes.size

    baseRoutes
      .map(_.metadata)
      .count(_.routeDefinition.predicates.exists(_ == MethodMatch(Get))) shouldBe 2 // two different paths, no user specific rate limits

    baseRoutes
      .map(_.metadata)
      .count(_.routeDefinition.predicates.exists(_ == MethodMatch(Post))) shouldBe 2 // One base + one user specific rate limits
  }

  it should "always include the uid filter unless it's a blacklist route" in {
    testableRouteDerivation
      .map(_.metadata)
      .filter(_.routeDefinition.customRoute.isEmpty)
      .map(_.routeDefinition.filters)
      .forall { filters =>
        filters.exists {
          case RequiredPrivileges(nel) if nel.toList.contains("uid") => true
          case _                                                     => false
        }
      } shouldBe true
  }

  "Globally Whitelisted Services" should "make every non Admin route a service specific route" in {
    val routes = testableWhitelistRoutesDerivation(sampleGloballyWhitelistedGateway)
      .filterNot(isAdminRoute)
      .filterNot(isCatchAllRoute)
      .filterNot(isHttpRejectRoute)

    routes.forall(route => isServiceSpecificRoute(route) || isWhitelistRejectRoute(route)) shouldBe true
  }

  it should "exclude non whitelisted service from these service specific routes" in {
    testableWhitelistRoutesDerivation(sampleGloballyWhitelistedGateway)
      .filterNot(isAdminRoute)
      .filterNot(isCatchAllRoute)
      .filterNot(isHttpRejectRoute)
      .filterNot(isWhitelistRejectRoute)
      .map { _.metadata.routeDefinition.predicates }
      .forall { _.contains(ClientMatch(WhitelistedUser)) } shouldBe true
  }

  it should "generate a 403 rejection for each whitelisted route" in {
    testableWhitelistRoutesDerivation(sampleGloballyWhitelistedGateway)
      .count(isWhitelistRejectRoute) shouldBe 3
  }

  it should "generate a whitelisting route for each path/operation combo" in {
    testableWhitelistRoutesDerivation(sampleGloballyWhitelistedGateway)
      .filterNot(isAdminRoute)
      .filterNot(isCatchAllRoute)
      .filterNot(isHttpRejectRoute)
      .filterNot(isWhitelistRejectRoute)
      .size shouldBe 3
  }

  it should "not have access to resource specific whitelisted routes unless explicitly declared" in {
    testableWhitelistRoutesDerivation(sampleResourceWhitelistingGateway)
      .filterNot(isAdminRoute)
      .filterNot(isCatchAllRoute)
      .filterNot(isHttpRejectRoute)
      .filter { route =>
        route.metadata.name.startsWith("whitelisted-gateway-post-api-resource-service")
      }
      .exists { route =>
        route.metadata.routeDefinition.predicates.contains(ClientMatch(WhitelistedUser))
      } shouldBe false
  }

  "Resource Whitelisting" should "restrict access only to listed services if resource specific whitelisting has been applied to a route" in {
    val updated = sampleResourceWhitelistingGateway.copy(globalWhitelistConfig = WhitelistConfig(Set(), Disabled))

    val routes = testableWhitelistRoutesDerivation(updated)
      .filterNot(isAdminRoute)
      .filterNot(isCatchAllRoute)
      .filterNot(isHttpRejectRoute)
      .filterNot(isWhitelistRejectRoute)
      .filter { route =>
        route.metadata.name.startsWith("whitelisted-gateway-post-api-resource")
      }

    routes.size shouldBe 1
    routes.head.metadata.routeDefinition.predicates.contains(ClientMatch(ResourceWhitelistedUser)) shouldBe true
  }

  it should "generate a rejection route for route level whitelisting" in {
    val updated = sampleResourceWhitelistingGateway.copy(globalWhitelistConfig = WhitelistConfig(Set(), Disabled))

    val routes = testableWhitelistRoutesDerivation(updated)
      .filterNot(isAdminRoute)
      .filterNot(isCatchAllRoute)
      .filterNot(isHttpRejectRoute)
      .filter { route =>
        route.metadata.name.startsWith("whitelisted-gateway-post-api-resource")
      }

    routes.map(_.metadata.name) should contain allOf (
      "whitelisted-gateway-post-api-resource-non-whitelisted",
      "whitelisted-gateway-post-api-resource-rl-service-resourcewhitelisteduser"
    )
  }

  it should "add services into the whitelist for a route even if they are not globally whitelisted" in {
    val routes = testableWhitelistRoutesDerivation(sampleResourceWhitelistingGateway)
      .filterNot(isAdminRoute)
      .filterNot(isCatchAllRoute)
      .filterNot(isHttpRejectRoute)

    routes
      .filter { route =>
        route.metadata.name.startsWith("whitelisted-gateway-post-api-resource-rl-service")
      }
      .exists { route =>
        route.metadata.routeDefinition.predicates.contains(ClientMatch(ResourceWhitelistedUser))
      } shouldBe true
  }

  it should "add services into the whitelist for a route even if they are in the global whitelist" in {
    val updated = sampleResourceWhitelistingGateway.copy(
      globalWhitelistConfig = WhitelistConfig(Set(WhitelistedUser, ResourceWhitelistedUser), Enabled))
    val routes = testableWhitelistRoutesDerivation(updated)
      .filterNot(isAdminRoute)
      .filterNot(isCatchAllRoute)
      .filterNot(isHttpRejectRoute)
      .filter { route =>
        route.metadata.name.startsWith("whitelisted-gateway-post-api-resource-rl-service")
      }

    routes.exists { route =>
      route.metadata.routeDefinition.predicates.contains(ClientMatch(ResourceWhitelistedUser))
    } shouldBe true
    routes.exists { route =>
      route.metadata.routeDefinition.predicates.contains(ClientMatch(WhitelistedUser))
    } shouldBe false
  }

  it should "maintain the scope restrictions for a resource whitelisted route" in {
    val routes = testableWhitelistRoutesDerivation(sampleResourceWhitelistingGateway)
      .filterNot(isAdminRoute)
      .filterNot(isCatchAllRoute)
      .filterNot(isHttpRejectRoute)
      .filter { route =>
        route.metadata.name.startsWith("whitelisted-gateway-post-api-resource-rl-service")
      }

    routes.exists { route =>
      route.metadata.routeDefinition.predicates.contains(ClientMatch(ResourceWhitelistedUser)) &&
      route.metadata.routeDefinition.filters.contains(RequiredPrivileges(NEL.of("uid", "service.write")))
    } shouldBe true
  }

  it should "override the global whitelisting if state is set to disabled" in {
    val routes = testableWhitelistRoutesDerivation(sampleResourceWhitelistingGateway)
      .filterNot(isAdminRoute)
      .filterNot(isCatchAllRoute)
      .filterNot(isHttpRejectRoute)

    val globallyWhitelistedRoute = routes.filter { route =>
      route.metadata.routeDefinition.predicates.contains(PathMatch("/api/resource")) && route.metadata.routeDefinition.predicates
        .contains(MethodMatch(Get))
    }.head
    val whitelistDisabledRoute = routes.filter(_.metadata.routeDefinition.predicates.contains(PathMatch("/api/resource/*"))).head
    val resourceWhitelistedRoute = routes.filter { route =>
      route.metadata.routeDefinition.predicates.contains(PathMatch("/api/resource")) && route.metadata.routeDefinition.predicates
        .contains(MethodMatch(Post))
    }.head

    globallyWhitelistedRoute.metadata.routeDefinition.predicates should contain(ClientMatch(WhitelistedUser))
    globallyWhitelistedRoute.metadata.routeDefinition.predicates should not contain ClientMatch(ResourceWhitelistedUser)

    whitelistDisabledRoute.metadata.routeDefinition.predicates should contain noneOf (ClientMatch(WhitelistedUser), ClientMatch(
      ResourceWhitelistedUser))

    resourceWhitelistedRoute.metadata.routeDefinition.predicates should not contain ClientMatch(WhitelistedUser)
    resourceWhitelistedRoute.metadata.routeDefinition.predicates should contain(ClientMatch(ResourceWhitelistedUser))
  }

  it should "not generate any service routes for an enabled empty global whitelist" in {
    val disableServiceAccessGateway =
      sampleGloballyWhitelistedGateway.copy(globalWhitelistConfig = WhitelistConfig(Set.empty[String], Enabled))
    val routes = testableWhitelistRoutesDerivation(disableServiceAccessGateway)
      .filterNot(isCatchAllRoute)
      .filterNot(isHttpRejectRoute)

    routes.size shouldBe 6
    routes.count(isAdminRoute) shouldBe 3
    routes.count(isWhitelistRejectRoute) shouldBe 3
  }

  it should "not generate any service specific routes for a empty resource whitelist" in {
    val updatedPaths: GatewayPaths = sampleResourceWhitelistingGateway.paths.map {
      case (path, conf) =>
        if (path == PathMatch("/api/resource/*")) {
          (path, conf.copy(operations = conf.operations.map {
            case (verb, actions) =>
              if (verb == Get) {
                (verb, actions.copy(resourceWhitelistConfig = WhitelistConfig(Set(), Enabled)))
              } else (verb, actions)
          }))
        } else (path, conf)
    }
    val disableServiceAccessResource = sampleResourceWhitelistingGateway.copy(paths = updatedPaths)
    val routes = testableWhitelistRoutesDerivation(disableServiceAccessResource)
      .filterNot(isCatchAllRoute)
      .filterNot(isHttpRejectRoute)

    routes.count(isAdminRoute) shouldBe 3
    routes.count(isWhitelistRejectRoute) shouldBe 3
    val serviceRoutes = routes.filterNot(isAdminRoute).filterNot(isWhitelistRejectRoute)
    serviceRoutes.size shouldBe 2
    serviceRoutes.flatMap(_.metadata.routeDefinition.predicates) should not contain PathMatch("/api/resource/*")
  }

  "Service Port" should "default to http when it is missing from the gateway spec" in {
    testableRouteDerivationWithCatchAll
      .flatMap(_.hostMappings)
      .map(_.services.head.portIdentifier) should contain only "http"
  }

  it should "use the provided value when it is set in the gateway spec" in {
    testableWhitelistRoutesDerivation(sampleGloballyWhitelistedGateway)
      .flatMap(_.hostMappings)
      .map(_.services.head.portIdentifier) should contain only "named"
  }

  "Secure Traffic Feature" should "all non-default routes have a Https traffic check" in {
    val routes = Await.result(
      ingressDerivationLogic.deriveRoutesFor(sampleGateway, GatewayMeta(DnsString("https-check-gw"), "ns")),
      10.seconds
    )

    val predicates = routes
      .filterNot(isCatchAllRoute)
      .filterNot(isHttpRejectRoute)
      .map(_.metadata.routeDefinition.predicates)

    predicates.forall(_.contains(HttpsTraffic)) shouldBe true
  }

  "Service Realm Check Feature" should "add a service realm filter to all non default routes" in {
    val routes = Await.result(
      ingressDerivationLogic.deriveRoutesFor(sampleGateway, GatewayMeta(DnsString("realm-check-test-1"), "ns")),
      10.seconds
    )

    val routeFilters = routes
      .filterNot(isCatchAllRoute)
      .filterNot(isHttpRejectRoute)
      .map(_.metadata.routeDefinition.filters)

    routeFilters should not be empty
    routeFilters.foreach { filters: List[SkipperFilter] =>
      filters should contain(NonCustomerRealm)
    }
  }

  it should "add routes for a user to access a whitelisted resource" in {
    val ingresses = testableWhitelistRoutesDerivation(sampleUserWhitelistingGateway)

    val userWhitelistedRoute = ingresses.find { route =>
      route.metadata.name == "whitelisted-gateway-get-api-resource-users-all"
    }.get
    val rateLimitedUserWhitelistedRoute = ingresses.find { route =>
      route.metadata.name == "whitelisted-gateway-get-api-resource-id-rl-users-all"
    }.get

    userWhitelistedRoute.metadata.routeDefinition.predicates should contain(UidMatch(NEL.of(WhitelistedUser)))
    rateLimitedUserWhitelistedRoute.metadata.routeDefinition.predicates should contain(UidMatch(NEL.of(WhitelistedUser)))
    rateLimitedUserWhitelistedRoute.metadata.routeDefinition.filters should contain(
      GlobalUsersRouteRateLimit("whitelisted-gateway", PathMatch("/api/resource/*"), MethodMatch(Get), 10, PerMinute))
  }

  it should "not add user whitelist routes when there is no user whitelist" in {
    val ingresses = testableWhitelistRoutesDerivation(sampleGateway)

    val allRoutes = ingresses
      .filterNot(isAdminRoute)
      .filterNot(isCatchAllRoute)
      .filterNot(isHttpRejectRoute)

    allRoutes.filter(isWhitelistedUserRoute) shouldBe empty
  }

  def isAdminRoute(route: IngressDefinition): Boolean = {
    val defn = route.metadata.routeDefinition
    defn.customRoute.isEmpty &&
    !defn.filters.exists(_.getClass == classOf[GlobalRouteRateLimit]) &&
    !defn.filters.exists(_.getClass == classOf[ClientSpecificRouteRateLimit]) &&
    route.metadata.name.endsWith("admins")
  }

  def isWhitelistedUserRoute(route: IngressDefinition): Boolean = {
    val defn = route.metadata.routeDefinition
    defn.customRoute.isEmpty &&
    !defn.filters.exists(_.getClass == classOf[GlobalRouteRateLimit]) &&
    !defn.filters.exists(_.getClass == classOf[ClientSpecificRouteRateLimit]) &&
    route.metadata.name.endsWith("-users-all")
  }

  def isCatchAllRoute(route: IngressDefinition): Boolean = {
    route.metadata.routeDefinition.customRoute.exists { customRoute =>
      NEL.of(Status(404), DefaultRejectMsg, Shunt).forall(customRoute.filters.toList.contains)
    }
  }

  def isHttpRejectRoute(route: IngressDefinition): Boolean = {
    route.metadata.routeDefinition.customRoute.exists { customRoute =>
      NEL.of(Status(400), HttpRejectMsg, Shunt).forall(customRoute.filters.toList.contains)
    }
  }

  def isServiceSpecificRoute(route: IngressDefinition): Boolean = {
    route.metadata.routeDefinition.predicates.exists(_.getClass == classOf[ClientMatch])
  }

  def isWhitelistRejectRoute(route: IngressDefinition): Boolean = {
    route.metadata.name.endsWith("-non-whitelisted")
  }
}
