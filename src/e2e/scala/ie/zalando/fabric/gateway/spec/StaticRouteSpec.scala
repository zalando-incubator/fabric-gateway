package ie.zalando.fabric.gateway.spec

import com.softwaremill.sttp.{HttpURLConnectionBackend, Id, RequestT, sttp}
import ie.zalando.fabric.gateway.{LoggingSttpBackend, TestConstants}
import org.scalatest.{FunSpec, Matchers}

class StaticRouteSpec extends FunSpec with Matchers {
  implicit val backend = new LoggingSttpBackend[Id, Nothing](HttpURLConnectionBackend())
  describe("Static Routes") {
    it("should show static response for valid tokens") {
      val resp = sttp
        .get(TestConstants.TestAppStaticRoute())
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .send()

      resp.code shouldBe 503
      resp.contentType shouldBe Some("application/problem+json")
      resp.header("X-Custom-Header") shouldBe Some("Some value")
      resp.body shouldBe Left("""{"title": "Service down for maintenance", "status": 503}""")
    }

    it("should still apply rate limits") {
      implicit val req: RequestT[Id, String, Nothing] = sttp
        .get(TestConstants.TestAppStaticRoute())
        .header("Authorization", s"Bearer ${TestConstants.ValidWhiteListToken}")

      val results = runReqs(5).filterNot(_ == 503)

      results.nonEmpty shouldBe true
      results.forall(_ == 429) shouldBe true
    }

    it("should reject requests with no token with a 401") {
      val resp = sttp.get(TestConstants.TestAppStaticRoute()).send()

      resp.code shouldBe 401
    }

    it("should reject requests which don't have the required scopes") {
      val resp = sttp
        .get(TestConstants.TestAppStaticRoute())
        .header("Authorization", s"Bearer ${TestConstants.ValidResourceWhiteListToken}")
        .send()

      resp.code shouldBe 403
    }
  }
}