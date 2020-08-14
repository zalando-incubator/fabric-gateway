package ie.zalando.fabric.gateway.service

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import ie.zalando.fabric.gateway.models.SynchDomain._
import ie.zalando.fabric.gateway.web.marshalling.JsonModels
import ie.zalando.fabric.gateway.service.TestUtils._
import org.mockito.scalatest.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import skuber.api.client.KubernetesClient

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class RouteWeightingsSpec extends FlatSpec with MockitoSugar with Matchers with JsonModels {

  implicit val ec: ExecutionContext   = ExecutionContext.global
  implicit val as: ActorSystem        = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()

  val kubernetesClient       = mock[KubernetesClient]
  val stackSetOperations     = new StackSetOperations(kubernetesClient)
  val ingressDerivationLogic = new IngressDerivationChain(stackSetOperations, None)

  val testableRouteDerivation: List[IngressDefinition] =
    Await.result(ingressDerivationLogic.deriveRoutesFor(sampleGateway,
      GatewayMeta(DnsString.fromString("gateway-name").get, "my-namespace")),
      10.seconds)

  "Route Weights" should "be higher for admin after dynamic traffic predicate is added to svc routes" in {
    val adminRouteWeight = testableRouteDerivation
      .filter { isAdminRoute }
      .map { route => route.metadata.routeDefinition.predicates }
      .find { routes => routes.contains(PathMatch("/api/resource/*"))}
      .get.foldLeft(0) { sumWeights }

    val svcRouteWeight = testableRouteDerivation
      .filter { isStandardServiceRoute }
      .map { route => route.metadata.routeDefinition.predicates }
      .find { routes => routes.contains(PathMatch("/api/resource/*"))}
      .get.foldLeft(0) { sumWeights }

    adminRouteWeight should be > svcRouteWeight
    adminRouteWeight should be > (svcRouteWeight + 1) // To cover dynamic Traffic predicate
  }

  it should "be higher for svc specific rate limit routes than global rate limit routes" in {
    val svcSpecificRateLimitedRoute = testableRouteDerivation
      .filter { isTargettedRateLimitRoute }
      .map { route => route.metadata.routeDefinition.predicates }
      .find { routes => routes.contains(PathMatch("/api/resource"))}
      .get.foldLeft(0) { sumWeights }

    val globalRateLimitedRoute = testableRouteDerivation
      .filter { isGlobalRateLimitRoute }
      .map { route => route.metadata.routeDefinition.predicates }
      .find { routes => routes.contains(PathMatch("/api/resource"))}
      .get.foldLeft(0) { sumWeights }

    svcSpecificRateLimitedRoute should be > globalRateLimitedRoute
    svcSpecificRateLimitedRoute should be > (globalRateLimitedRoute + 1) // To cover dynamic Traffic predicate
  }

  private def sumWeights(sum: Int, predicate: SkipperPredicate): Int = sum + (predicate match {
      case WeightedRoute(weight) => weight.weight
      case _ => 1
    })
}
