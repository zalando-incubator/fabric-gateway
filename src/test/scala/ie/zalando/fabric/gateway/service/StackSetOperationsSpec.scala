package ie.zalando.fabric.gateway.service

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.{FlatSpec, Matchers}
import skuber._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

class StackSetOperationsSpec extends FlatSpec with Matchers {

  // Currently used for test dev
  ignore should "be able to retrieve an existing StackSet" in {
    implicit val system: ActorSystem                  = ActorSystem("Test-Actor-System")
    implicit val materializer: ActorMaterializer      = ActorMaterializer()
    implicit val dispatcher: ExecutionContextExecutor = system.dispatcher

    val k8sClient = k8sInit
    val ssOps     = new StackSetOperations(k8sClient)

    val op = Await.result(ssOps.getStatus(StackSetIdentifer("fmoloney-traffic", "default")), 20.seconds)

    op match {
      case Some(status) => assert(status.traffic.isEmpty)
      case None         => fail("Status Not found")
    }
  }
}
