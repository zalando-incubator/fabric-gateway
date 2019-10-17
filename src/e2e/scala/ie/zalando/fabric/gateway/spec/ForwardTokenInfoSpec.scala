package ie.zalando.fabric.gateway.spec

import com.softwaremill.sttp._
import ie.zalando.fabric.gateway.{LoggingSttpBackend, TestConstants}
import org.scalatest._

class ForwardTokenInfoSpec extends FunSpec with Matchers {

  implicit val backend = new LoggingSttpBackend[Id, Nothing](HttpURLConnectionBackend())

  describe("Fabric Gateway Token Info Forwarder") {

    it("should fwd the token info details to the app") {
      val resp = sttp
        .get(TestConstants.TestAppResourceById(123))
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .send()

      resp.code shouldBe 200
      resp.body match {
        case Right(body) =>
          body should include(
            "X-Tokeninfo-Forward: {\"realm\":\"/services\",\"scope\":[\"fabric-demo-app.read\",\"fabric-demo-app.write\",\"uid\"],\"uid\":\"stups_fabric-demo-app\"}")
        case _ => fail("Body was not present in response")
      }
    }
  }
}
