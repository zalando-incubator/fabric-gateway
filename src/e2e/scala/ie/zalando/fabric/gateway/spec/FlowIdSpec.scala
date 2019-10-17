package ie.zalando.fabric.gateway.spec

import com.softwaremill.sttp._
import ie.zalando.fabric.gateway.{LoggingSttpBackend, TestConstants}
import org.scalatest._

class FlowIdSpec extends FunSpec with Matchers {

  implicit val backend = new LoggingSttpBackend[Id, Nothing](HttpURLConnectionBackend())

  describe("Fabric Gateway Flow Id Filter") {

    it("should not change the flow id if it's already provided") {
      val resp = sttp
        .get(TestConstants.TestAppResourceById(123))
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .header("X-Flow-Id", "PROVIDED-BY-CALLER")
        .send()

      resp.code shouldBe 200
      resp.body.exists(_.contains("X-Flow-Id: PROVIDED-BY-CALLER")) shouldBe true
    }

    it("should add a generated Flow Id header if none is provided") {
      val resp = sttp
        .get(TestConstants.TestAppResourceById(123))
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .send()

      resp.code shouldBe 200
      resp.body.exists(_.contains("X-Flow-Id:")) shouldBe true
    }
  }
}
