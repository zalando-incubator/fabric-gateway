package ie.zalando.fabric.gateway.spec

import com.softwaremill.sttp._
import ie.zalando.fabric.gateway.{LoggingSttpBackend, TestConstants}
import org.scalatest._

class RejectHttpSpec extends FunSpec with Matchers {

  implicit val backend = new LoggingSttpBackend[Id, Nothing](HttpURLConnectionBackend())

  describe("Fabric Gateway will reject not https traffic") {

    it("should offer a redirect to https for http traffic from valid services") {
      val resp = sttp
        .get(TestConstants.TestAppResources(scheme = "http"))
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .followRedirects(false)
        .send()

      resp.code shouldBe 308
      resp.header("Location") shouldBe Some("https://test-gateway-operator.playground.zalan.do/resources")
    }
  }
}
