package ie.zalando.fabric.gateway.spec

import com.softwaremill.sttp._
import ie.zalando.fabric.gateway.{LoggingSttpBackend, TestConstants}
import org.scalatest._

class RejectHttpSpec extends FunSpec with Matchers {

  implicit val backend = new LoggingSttpBackend[Id, Nothing](HttpURLConnectionBackend())

  describe("Fabric Gateway will reject not https traffic") {

    it("should reject http traffic from valid services with a 403") {
      val resp = sttp
        .get(TestConstants.TestAppResources(scheme = "http"))
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .send()

      resp.code shouldBe 400

      resp.body match {
        case Left(body) =>
          body shouldBe """{"title":"Gateway Rejected","status":400,"detail":"TLS is required","type":"https://cloud.docs.zalando.net/howtos/ingress/#redirect-http-to-https"}"""
        case _ => fail("Expecting an err response")
      }
    }
  }
}
