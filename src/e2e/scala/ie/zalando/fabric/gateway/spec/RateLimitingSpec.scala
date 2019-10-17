package ie.zalando.fabric.gateway.spec

import com.softwaremill.sttp._
import ie.zalando.fabric.gateway.{LoggingSttpBackend, TestConstants}
import org.scalatest._

import scala.annotation.tailrec

class RateLimitingSpec extends FunSpec with Matchers {

  implicit val backend = new LoggingSttpBackend[Id, Nothing](HttpURLConnectionBackend())

  private val RequestBlitzSize = 50
  private val HttpOk           = 200
  private val RateLimited      = 429

  @tailrec
  private def runReqs(i: Int, results: List[Int] = Nil)(implicit req: RequestT[Id, String, Nothing]): List[Int] = i match {
    case 0 => results
    case _ => runReqs(i - 1, req.send().code :: results)
  }

  describe("Fabric Gateway Rate Limiting") {

    it("should apply default rate limiting to everyone") {
      implicit val req = sttp
        .get(TestConstants.RateLimitedForAll())
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")

      val results = runReqs(RequestBlitzSize).filterNot(_ == HttpOk)

      results.nonEmpty shouldBe true
      results.forall(_ == RateLimited) shouldBe true
      results.size should not be RequestBlitzSize
    }

    // Seems to be a skipper bug where rate limiting is not restricted to the route
    it("should apply service specific rate limits") {
      implicit val req = sttp
        .get(TestConstants.RateLimitedForMe())
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")

      val results = runReqs(RequestBlitzSize).filterNot(_ == HttpOk)

      results.nonEmpty shouldBe true
      results.forall(_ == RateLimited) shouldBe true
      results.size should not be RequestBlitzSize
    }

    it("should not apply service specific rate limits if the service id does not match") {
      implicit val req = sttp
        .get(TestConstants.RateLimitedForOther())
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")

      val results = runReqs(RequestBlitzSize).dropWhile(_ == HttpOk)
      results shouldBe empty
    }
  }
}
