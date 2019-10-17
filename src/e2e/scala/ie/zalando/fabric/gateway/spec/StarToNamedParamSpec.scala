package ie.zalando.fabric.gateway.spec

import com.softwaremill.sttp._
import ie.zalando.fabric.gateway.{LoggingSttpBackend, TestConstants}
import org.scalatest._

class StarToNamedParamSpec extends FunSpec with Matchers {

  implicit val backend = new LoggingSttpBackend[Id, Nothing](HttpURLConnectionBackend())

  describe("Routes with a * for identifying id segments should work as normal") {
    it("should pass through requests which have a valid token") {
      val resp = sttp
        .get(TestConstants.TestAppStarById(123))
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .send()

      resp.code shouldBe 200
    }

    it("should not allow access to paths below the * segment") {
      val resp = sttp
        .get(TestConstants.TestInvalidAppStar())
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .send()

      resp.code shouldBe 404
    }
  }
}
