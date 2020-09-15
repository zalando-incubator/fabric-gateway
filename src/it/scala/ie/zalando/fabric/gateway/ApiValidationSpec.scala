package ie.zalando.fabric.gateway

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import ie.zalando.fabric.gateway.TestJsonModels.{TestSynchResponse, TestValidationResponse}
import ie.zalando.fabric.gateway.TestUtils.TestData._
import ie.zalando.fabric.gateway.TestUtils._
import ie.zalando.fabric.gateway.service.{IngressDerivationChain, StackSetOperations, ZeroDowntimeIngressTransitions}
import ie.zalando.fabric.gateway.web.{GatewayWebhookRoutes, OperationalRoutes}
import io.circe.Json
import org.mockito.scalatest.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec, Matchers}
import skuber.api.client.KubernetesClient
import skuber.k8sInit

import scala.concurrent.duration._

class ApiValidationSpec
    extends FlatSpec
    with MockitoSugar
    with Matchers
    with ScalatestRouteTest
    with OperationalRoutes
    with GatewayWebhookRoutes
    with TestJsonModels
    with BeforeAndAfterEach
    with BeforeAndAfterAll {

  val kubernetesClient: KubernetesClient = k8sInit
  val stackSetOperations                 = new StackSetOperations(kubernetesClient)
  val ingressDerivationLogic             = new IngressDerivationChain(stackSetOperations, None)
  val ingressTransitions                 = new ZeroDowntimeIngressTransitions(ingressDerivationLogic)

  var wireMockServer: WireMockServer = _

  override def beforeAll(): Unit = {
    if (System.getenv("SKUBER_URL")  == null) {
      throw new IllegalArgumentException("ApiValidationSpec requires env var 'SKUBER_URL' to be set to 'http://localhost:8001'")
    }
  }

  override def beforeEach(): Unit = {
    wireMockServer = new WireMockServer(
      WireMockConfiguration
        .wireMockConfig()
        .port(8001)
        .withRootDirectory("src/it/resources/wiremock")
    )
    wireMockServer.start()
  }

  override def afterEach(): Unit = wireMockServer.stop()

  "Gateway Controller API" should "expose a health endpoints" in {
    Get("/health") ~> operationalRoutes ~> check {
      response.status shouldBe StatusCodes.OK
    }
  }

  it should "return a bad request if you do not post a payload in the synch request" in {
    Post("/synch") ~> Route.seal(createRoutesFromDerivations(ingressTransitions)) ~> check {
      response.status shouldBe StatusCodes.BadRequest
    }
  }

  it should "return a bad request if you post an invalid payload in the synch request" in {
    synchRequest(InvalidRequest.payload) ~> Route.seal(createRoutesFromDerivations(ingressTransitions)) ~> check {
      response.status shouldBe StatusCodes.BadRequest
    }
  }

  it should "return OK for a valid payload" in {
    synchRequest(ValidSynchRequest.payload) ~> Route.seal(createRoutesFromDerivations(ingressTransitions)) ~> check {
      response.status shouldBe StatusCodes.OK
    }
  }

  it should "return OK for a valid payload with named path parameters" in {
    synchRequest(ValidSynchRequestWithNamedPathParameters.payload) ~> Route.seal(createRoutesFromDerivations(ingressTransitions)) ~> check {
      response.status shouldBe StatusCodes.OK
    }
  }

  it should "return OK for a valid payload with whitelisting" in {
    synchRequest(ValidWhitelistSynchRequest.payload) ~> Route.seal(createRoutesFromDerivations(ingressTransitions)) ~> check {
      response.status shouldBe StatusCodes.OK
    }
  }

  it should "return ingresses in the same namespace as the fabric gateway definition" in {
    synchRequest(ValidSynchRequest.payload) ~> Route.seal(createRoutesFromDerivations(ingressTransitions)) ~> check {
      val ingressii = responseAs[TestSynchResponse].ingressii
      ingressii.forall(_.namespace == "some-namespace") shouldBe true
    }
  }

  it should "return ingresses in the same labels as the fabric gateway definition" in {
    synchRequest(ValidSynchRequest.payload) ~> Route.seal(createRoutesFromDerivations(ingressTransitions)) ~> check {
      val ingressii = responseAs[TestSynchResponse].ingressii
      ingressii.forall { ingress =>
        ingress.labels.get("application").contains("my-app-id") && ingress.labels.get("component").contains("my-component-label")
      } shouldBe true
    }
  }

  it should "sanitize incoming gateway names to ensure that ingress DNS entries can be created" in {
    synchRequest(ValidSynchRequest.payload) ~> Route.seal(createRoutesFromDerivations(ingressTransitions)) ~> check {
      val ingressii = responseAs[TestSynchResponse].ingressii
      ingressii.forall(_.name.startsWith("my-app-gateway")) shouldBe true
    }
  }

  it should "ensure that all admin routes have extra auditing enabled" in {
    synchRequest(ValidSynchRequest.payload) ~> Route.seal(createRoutesFromDerivations(ingressTransitions)) ~> check {
      val ingressii   = responseAs[TestSynchResponse].ingressii
      val adminRoutes = ingressii.filter(_.name.contains("admins")).map(_.filters.get)
      adminRoutes should not be empty
      adminRoutes.foreach { filterChain =>
        filterChain should include(
          """enableAccessLog(2, 4, 5) -> unverifiedAuditLog("https://identity.zalando.com/managed-id")""")
      }
    }
  }

  it should "add routes for whitelisted users to access a resource without scope checks" in {
    synchRequest(ValidWhitelistSynchRequest.payload) ~> Route.seal(createRoutesFromDerivations(ingressTransitions)) ~> check {
      val ingressii = responseAs[TestSynchResponse].ingressii
      val userWhitelistIngress = ingressii
        .find(_.name == "my-app-gateway-post-api-resource-rl-users-all")
        .get

      userWhitelistIngress.predicates.get should include(
        """JWTPayloadAnyKV("https://identity.zalando.com/managed-id", "whitelisted_user")""")
      userWhitelistIngress.filters.get should include(
        """clusterClientRatelimit("my-app-gateway_api-resource_POST_users", 10, "1m", "Authorization")""")
    }
  }

  it should "be able to properly deserialize the request payload" in {
    validationRequest(ValidValidationRequest.payload) ~> Route.seal(createRoutesFromDerivations(ingressTransitions)) ~> check {
      response.status shouldBe StatusCodes.OK
    }
  }

  it should "send an allow response for a valid payload" in {
    validationRequest(ValidValidationRequest.payload) ~> Route.seal(createRoutesFromDerivations(ingressTransitions)) ~> check {
      val resp = responseAs[TestValidationResponse]
      resp.allowed shouldBe true
    }
  }

  it should "send a disallow response for an invalid payload" in {
    validationRequest(ValidationRequestForNoPaths.payload) ~> Route.seal(createRoutesFromDerivations(ingressTransitions)) ~> check {
      val resp = responseAs[TestValidationResponse]
      resp.allowed shouldBe false
      resp.uid shouldBe "failing-uid-1"
      resp.status.get.reason shouldBe "There must be at least 1 path defined"
    }
  }

  it should "handle the weird empty create state gracefully" in {
    validationRequest(ValidationRequestForInvalidInput.payload) ~> Route.seal(createRoutesFromDerivations(ingressTransitions)) ~> check {
      val resp = responseAs[TestValidationResponse]
      resp.allowed shouldBe false
      resp.uid shouldBe "formatting-errors"
      resp.status.get.reason shouldBe "There must be at least 1 path defined, You must have 1 of the `x-fabric-service` or `x-external-service-provider` keys defined"
    }
  }

  it should "successfully validate a stackset integrated resource which has no services defined" in {
    validationRequest(ValidationRequestForValidStacksetIntegration.payload) ~> Route.seal(
      createRoutesFromDerivations(ingressTransitions)) ~> check {
      val resp = responseAs[TestValidationResponse]
      resp.allowed shouldBe true
    }
  }

  it should "reject a stackset integrated resource which also has a service defined" in {
    validationRequest(ValidationRequestForInvalidServiceDefinition.payload) ~> Route.seal(
      createRoutesFromDerivations(ingressTransitions)) ~> check {
      val resp = responseAs[TestValidationResponse]
      resp.allowed shouldBe false
      resp.uid shouldBe "failing-uid-service-and-ssint-defined"
      resp.status.get.reason shouldBe "You cannot define services with the `x-fabric-service` key and also set external management using `x-external-service-provider`"
    }
  }

  // Tests were timing out with the mock...
  implicit val routeTestTimeout: RouteTestTimeout = RouteTestTimeout(5.seconds)

  it should "accept a stackset managed resource and create the ingress based on the response from the K8s API" in {
    synchRequest(ValidSynchRequestWithStackSetManagedServices.payload) ~> Route.seal(
      createRoutesFromDerivations(ingressTransitions)) ~> check {
      val ingressii = responseAs[TestSynchResponse].ingressii
      ingressii should have length 13
      ingressii.map(_.rules.head.paths.map(_.serviceName)).foreach { backends =>
        backends should contain only ("my-test-stackset-svc1", "my-test-stackset-svc2")
      }
    }
  }

  it should "fail if an unexpected response comes back from the K8s client" in {
    synchRequest(BogusStackSetTriggeringRequest.payload) ~> Route.seal(createRoutesFromDerivations(ingressTransitions)) ~> check {
      response.status shouldBe StatusCodes.InternalServerError
    }
  }

  it should "add extra gateways with versioned hosts for a stackset-managed gateway when configured" in {
    synchRequest(ValidSynchRequestWithStackSetManagedServices.payload) ~> Route.seal(
      createRoutesFromDerivations(new ZeroDowntimeIngressTransitions(
        new IngressDerivationChain(stackSetOperations, Some("smart-product-platform-test.zalan.do"))))) ~> check {
      val ingressii = responseAs[TestSynchResponse].ingressii

      val mainIngressii = ingressii.filter(_.rules.map(_.host).contains("my-app.smart-product-platform-test.zalan.do"))
      val versionedHost1 =
        ingressii.filter(_.rules.map(_.host).contains("my-test-stackset-svc1.smart-product-platform-test.zalan.do"))
      val versionedHost2 =
        ingressii.filter(_.rules.map(_.host).contains("my-test-stackset-svc2.smart-product-platform-test.zalan.do"))

      mainIngressii should have length 13
      versionedHost1 should have length 13
      versionedHost2 should have length 13

      mainIngressii.flatMap(_.rules.map(_.paths.map(_.serviceName))).foreach { backends =>
        backends should contain only ("my-test-stackset-svc1", "my-test-stackset-svc2")
      }
      mainIngressii.map(_.allAnnos).foreach { annos =>
        annos should contain(
          "zalando.org/backend-weights" -> Json.fromString("{\"my-test-stackset-svc1\":80.1,\"my-test-stackset-svc2\":19.9}"))
      }
      versionedHost1.flatMap(_.rules.map(_.paths.map(_.serviceName))).foreach { backends =>
        backends should contain only "my-test-stackset-svc1"
      }
      versionedHost2.flatMap(_.rules.map(_.paths.map(_.serviceName))).foreach { backends =>
        backends should contain only "my-test-stackset-svc2"
      }
      (versionedHost1 ++ versionedHost2).map(_.allAnnos).foreach { annos =>
        annos.keys should not contain "zalando.org/backend-weights"
      }
      // all ingress names should be unique
      ingressii.map(_.name).toSet.size shouldBe ingressii.size
    }
  }

  it should "accept a stackset managed resource but create no ingress because the stackset can't be found from the K8s API" in {
    synchRequest(ValidSynchRequestWithNonExistingStackSetManagingServices.payload) ~> Route.seal(
      createRoutesFromDerivations(ingressTransitions)) ~> check {
      val ingressii = responseAs[TestSynchResponse].ingressii
      ingressii should have length 0
    }
  }

  it should "accept a stackset managed resource where the stackset uses a named port as the backendPort" in {
    synchRequest(ValidSynchRequestWithStackSetManagingServicesAndNamedPort.payload) ~> Route.seal(
      createRoutesFromDerivations(ingressTransitions)) ~> check {
      val ingressii = responseAs[TestSynchResponse].ingressii
      ingressii should have length 5
    }
  }

  it should "accept a stackset managed resource but create no ingress because the stackset exists but doesn't have the traffic key in the status" in {
    synchRequest(ValidSynchRequestWithStackSetManagingServicesButNotTrafficStatus.payload) ~> Route.seal(
      createRoutesFromDerivations(ingressTransitions)) ~> check {
      val ingressii = responseAs[TestSynchResponse].ingressii
      ingressii should have length 0
    }
  }

  it should "Create routes for handling OPTIONS requests when CORS is enabled" in {
    synchRequest(ValidSynchRequestWithCorsEnabled.payload) ~> Route.seal(createRoutesFromDerivations(ingressTransitions)) ~> check {
      val ingressii = responseAs[TestSynchResponse].ingressii
      val corsRoutes = ingressii
        .filter(_.name.contains("cors"))
        .flatMap(_.route)
      corsRoutes.size should be > 0
      corsRoutes should contain theSameElementsAs List(
        """Path("/api/resource") && Method("OPTIONS") && Header("X-Forwarded-Proto", "https") -> enableAccessLog(4, 5) -> status(204) -> flowId("reuse") -> corsOrigin("https://example.com", "https://example-other.com") -> appendResponseHeader("Access-Control-Allow-Methods", "POST, OPTIONS") -> appendResponseHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Flow-Id") -> <shunt>""",
        """Path("/api/resource/:id") && Method("OPTIONS") && Header("X-Forwarded-Proto", "https") -> enableAccessLog(4, 5) -> status(204) -> flowId("reuse") -> corsOrigin("https://example.com", "https://example-other.com") -> appendResponseHeader("Access-Control-Allow-Methods", "GET, PATCH, PUT, OPTIONS") -> appendResponseHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Flow-Id") -> <shunt>""",
        """Path("/events") && Method("OPTIONS") && Header("X-Forwarded-Proto", "https") -> enableAccessLog(4, 5) -> status(204) -> flowId("reuse") -> corsOrigin("https://example.com", "https://example-other.com") -> appendResponseHeader("Access-Control-Allow-Methods", "POST, OPTIONS") -> appendResponseHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Flow-Id") -> <shunt>"""
      )
    }
  }
}
