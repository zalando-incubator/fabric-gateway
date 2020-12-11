package ie.zalando.fabric.gateway.spec

import com.softwaremill.sttp.StatusCodes.{Ok, TooManyRequests}
import com.softwaremill.sttp.{HttpURLConnectionBackend, Id, sttp}
import ie.zalando.fabric.gateway.{LoggingSttpBackend, TestConstants}
import org.scalatest._

class RateLimitingSpec extends FunSpec with Matchers {

  implicit val backend = new LoggingSttpBackend[Id, Nothing](HttpURLConnectionBackend())

  private val RequestBlitzSize = 50

  describe("Fabric Gateway Rate Limiting") {

    it("should apply default rate limiting to everyone") {
      implicit val req = sttp
        .get(TestConstants.RateLimitedForAll())
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")

      val results = runReqs(RequestBlitzSize).filterNot(_.status == Ok)

      results.nonEmpty shouldBe true
      results.size should not be RequestBlitzSize
      results.map(_.status) should contain only TooManyRequests
      all(results.map(_.body)) should include("\"title\": \"Rate limit exceeded\"")
      all(results.map(r => getHeader("Content-Type", r.headers))) shouldBe Some("application/problem+json")
    }

    // Seems to be a skipper bug where rate limiting is not restricted to the route
    it("should apply service specific rate limits") {
      implicit val req = sttp
        .get(TestConstants.RateLimitedForMe())
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")

      val results = runReqs(RequestBlitzSize).filterNot(_.status == Ok)

      results.nonEmpty shouldBe true
      results.size should not be RequestBlitzSize
      results.map(_.status) should contain only TooManyRequests
      all(results.map(_.body)) should include("\"title\": \"Rate limit exceeded\"")
      all(results.map(r => getHeader("Content-Type", r.headers))) shouldBe Some("application/problem+json")
    }

    it("should not apply service specific rate limits if the service id does not match") {
      implicit val req = sttp
        .get(TestConstants.RateLimitedForOther())
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")

      val results = runReqs(RequestBlitzSize).dropWhile(_.status == Ok)
      results shouldBe empty
    }
  }
}
