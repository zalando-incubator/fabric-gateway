package ie.zalando.fabric.gateway.service

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.data.{NonEmptyList => NEL}
import ie.zalando.fabric.gateway.models.HttpModels
import ie.zalando.fabric.gateway.models.SynchDomain._
import ie.zalando.fabric.gateway.web.marshalling.JsonModels
import org.mockito.scalatest.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import skuber.api.client.KubernetesClient

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class ZeroDowntimeIngressTransitionsSpec extends FlatSpec with MockitoSugar with Matchers with JsonModels {

  implicit val ec: ExecutionContext   = ExecutionContext.global
  implicit val as: ActorSystem        = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()

  private val kubernetesClient       = mock[KubernetesClient]
  private val stackSetOperations     = new StackSetOperations(kubernetesClient)
  private val ingressDerivationLogic = new IngressDerivationChain(stackSetOperations, None)
  private val ingressTransitions     = new ZeroDowntimeIngressTransitions(ingressDerivationLogic)

  private val AdminUser                        = "adminUser"
  private val InheritedWhitelistDetails        = WhitelistConfig(Set(), GlobalWhitelistConfigInherited)
  private val UserWhitelist                    = EmployeeAccessConfig(AllowList(Set.empty))
  private val DisabledCors: Option[CorsConfig] = None
  private val withoutRateLimiting = ActionAuthorizations(
    NEL.of("uid", "service.read"),
    None,
    InheritedWhitelistDetails,
    UserWhitelist
  )
  private val withRateLimiting =
    withoutRateLimiting.copy(rateLimit = Some(RateLimitDetails(10, PerMinute, Map.empty[String, Int])))

  private val initialGatewaySpec = GatewaySpec(
    SchemaDefinedServices(Set(IngressBackend("host", Set(ServiceDescription("svc"))))),
    Set(AdminUser),
    WhitelistConfig(Set(), Disabled),
    DisabledCors,
    EmployeeAccessConfig(ScopedAccess),
    None,
    Map(
      PathMatch("/api/resource") -> PathConfig(
        Map(
          Get -> withoutRateLimiting
        ))
    )
  )
  private val updatedGatewaySpec = initialGatewaySpec.copy(
    paths = Map(
      PathMatch("/api/resource") -> PathConfig(
        Map(
          Get -> withRateLimiting
        ))
    )
  )

  def getRoutes(gw: GatewaySpec, existingRoutes: Seq[IngressDefinition], isLegacy: Boolean): List[IngressDefinition] =
    Await.result(
      ingressTransitions.defineSafeRouteTransition(
        gw,
        GatewayMeta(DnsString.fromString("gateway-name").get, "my-namespace", None, Map.empty),
        existingRoutes,
        isLegacy),
      10.seconds
    )

  it should "create new routes alongside existing before deleting any old routes" in {
    val initialRoutes = getRoutes(initialGatewaySpec, List.empty, isLegacy = true)
    initialRoutes.size shouldBe 4
    initialRoutes.map(_.metadata.name) should contain allOf (
      "gateway-name-default-404-route",
      "gateway-name-reject-http-route",
      "gateway-name-get-api-resource-admins",
      "gateway-name-get-api-resource-all"
    )

    val transitionRoutes = getRoutes(updatedGatewaySpec, initialRoutes, isLegacy = true)
    transitionRoutes.size shouldBe 5
    transitionRoutes.map(_.metadata.name) should contain allOf (
      "gateway-name-default-404-route",
      "gateway-name-reject-http-route",
      "gateway-name-get-api-resource-admins",
      "gateway-name-get-api-resource-rl-all",
      "gateway-name-get-api-resource-all"
    )

    val finalRoutes = getRoutes(updatedGatewaySpec, transitionRoutes, isLegacy = true)
    finalRoutes.size shouldBe 4
    finalRoutes.map(_.metadata.name) should contain allOf (
      "gateway-name-default-404-route",
      "gateway-name-reject-http-route",
      "gateway-name-get-api-resource-admins",
      "gateway-name-get-api-resource-rl-all"
    )

    finalRoutes.map(_.apiVersion) should contain only(HttpModels.LegacyIngressApiVersion)
  }

  it should "use the new migration naming for non legacy routes" in {
    val initialRoutes = getRoutes(initialGatewaySpec, List.empty, isLegacy = false)
    initialRoutes.size shouldBe 4
    initialRoutes.map(_.metadata.name) should contain allOf (
      "m-gateway-name-default-404-route",
      "m-gateway-name-reject-http-route",
      "m-gateway-name-get-api-resource-admins",
      "m-gateway-name-get-api-resource-all"
    )

    initialRoutes.map(_.apiVersion) should contain only(HttpModels.IngressApiVersion)
  }

}
