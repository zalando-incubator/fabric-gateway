package ie.zalando.fabric.gateway.spec

import com.softwaremill.sttp.{HttpURLConnectionBackend, Id, sttp}
import ie.zalando.fabric.gateway.{LoggingSttpBackend, TestConstants}
import org.scalatest.{FunSpec, Matchers}

class StaticRouteSpec extends FunSpec with Matchers {
  implicit val backend = new LoggingSttpBackend[Id, Nothing](HttpURLConnectionBackend())

  describe("Static Routes") {
    it("should reject requests with no token with a 401") {
      val resp = sttp.get(TestConstants.TestAppResourceById(123)).send()

      resp.code shouldBe 401
    }
  }
}