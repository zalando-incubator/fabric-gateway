package ie.zalando.fabric.gateway.service

import akka.http.scaladsl.model.Uri
import cats.data.{NonEmptyList => NEL}
import ie.zalando.fabric.gateway.models.SynchDomain._

object TestUtils {

  // Test Data
  val AdminUser                 = "adminUser"
  val WhitelistedUser           = "whitelistedUser"
  val ResourceWhitelistedUser   = "resourceWhitelistedUser"
  val InheritedWhitelistDetails = WhitelistConfig(Set(), Inherited)
  val UserWhitelist             = EmployeeAccessConfig(Set.empty)
  val EnabledCors = Some(
    CorsConfig(Set(Uri.from(host = "example.com"), Uri.from(host = "example-other.com")),
               Set("Content-Type", "Authorization", "X-Flow-id")))
  val DisabledCors: Option[CorsConfig] = None

  val sampleGateway = GatewaySpec(
    SchemaDefinedServices(Set(IngressBackend("host", Set(ServiceDescription("svc"))))),
    Set(AdminUser),
    WhitelistConfig(Set(), Disabled),
    DisabledCors,
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
    SchemaDefinedServices(Set(IngressBackend("host", Set(ServiceDescription("svc", NamedServicePort("named")))))),
    Set(AdminUser),
    WhitelistConfig(Set(WhitelistedUser), Enabled),
    DisabledCors,
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
    SchemaDefinedServices(Set(IngressBackend("host", Set(ServiceDescription("svc", NamedServicePort("named")))))),
    Set(AdminUser),
    WhitelistConfig(Set(WhitelistedUser), Enabled),
    DisabledCors,
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
    SchemaDefinedServices(Set(IngressBackend("host", Set(ServiceDescription("svc", NamedServicePort("named")))))),
    Set(AdminUser),
    WhitelistConfig(Set(WhitelistedUser), Enabled),
    DisabledCors,
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

  // Utility Functions
  def isAdminRoute(route: IngressDefinition): Boolean = {
    val defn = route.metadata.routeDefinition
    defn.customRoute.isEmpty &&
    !defn.filters.exists(_.getClass == classOf[GlobalRouteRateLimit]) &&
    !defn.filters.exists(_.getClass == classOf[ClientSpecificRouteRateLimit]) &&
    route.metadata.name.endsWith("admins")
  }

  def isCorsRoute(route: IngressDefinition): Boolean = {
    val metadata = route.metadata
    metadata.name.contains("-cors") && metadata.routeDefinition.customRoute.isDefined
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

  def isTargettedRateLimitRoute(route: IngressDefinition): Boolean = {
    route.metadata.routeDefinition.filters.exists(_.getClass == classOf[ClientSpecificRouteRateLimit])
  }

  def isGlobalRateLimitRoute(route: IngressDefinition): Boolean = {
    route.metadata.routeDefinition.filters.exists(_.getClass == classOf[GlobalRouteRateLimit])
  }

  def isWhitelistRejectRoute(route: IngressDefinition): Boolean = {
    route.metadata.name.endsWith("-non-whitelisted")
  }

  def isStandardServiceRoute(route: IngressDefinition): Boolean = {
    val defn = route.metadata.routeDefinition
    defn.customRoute.isEmpty &&
    !defn.filters.exists(_.getClass == classOf[GlobalRouteRateLimit]) &&
    !defn.filters.exists(_.getClass == classOf[ClientSpecificRouteRateLimit]) &&
    route.metadata.name.endsWith("-all") &&
    !isWhitelistedUserRoute(route)
  }
}
