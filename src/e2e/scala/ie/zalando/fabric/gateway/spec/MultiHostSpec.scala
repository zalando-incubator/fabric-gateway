package ie.zalando.fabric.gateway.spec

import com.softwaremill.sttp._
import ie.zalando.fabric.gateway.{LoggingSttpBackend, TestConstants}
import org.scalatest.{FunSpec, Matchers}

class MultiHostSpec extends FunSpec with Matchers {

  implicit val backend = new LoggingSttpBackend[Id, Nothing](HttpURLConnectionBackend())

  describe("Fabric Gateway Multi Host Feature") {

    it("should allow you to query the same backend from multiple hosts") {
      val primaryResp   = buildRequest().send()
      val secondaryResp = buildRequest(TestConstants.AlternateTestHost).send()

      primaryResp.code shouldBe 200
      secondaryResp.code shouldBe 200
    }
  }

  private def buildRequest(host: String = TestConstants.TestAppHost) =
    sttp
      .get(TestConstants.TestAppResourceById(123, host))
      .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
}
