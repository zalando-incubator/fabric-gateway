package ie.zalando.fabric.gateway

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ie.zalando.fabric.gateway.TestUtils.TestData.ValidSynchRequest
import ie.zalando.fabric.gateway.TestUtils._
import ie.zalando.fabric.gateway.service.{IngressDerivationChain, StackSetOperations}
import ie.zalando.fabric.gateway.web.{GatewayWebhookRoutes, OperationalRoutes}
import org.mockito.scalatest.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import skuber.api.client.KubernetesClient

class SPP3240BugVerificationSpec
    extends FlatSpec
    with MockitoSugar
    with Matchers
    with ScalatestRouteTest
    with OperationalRoutes
    with GatewayWebhookRoutes {

  val k8sClient = mock[KubernetesClient]
  val ssOps     = new StackSetOperations(k8sClient)
  val ingDeriv  = new IngressDerivationChain(ssOps)

  "Gateway Controller API" should "be able to handle the ingress creation response from metacontroller" in {
    synchRequest(ValidSynchRequest.payload) ~> Route.seal(createRoutesFromDerivations(ingDeriv)) ~> check {
      response.status shouldBe StatusCodes.OK
    }
  }
}
