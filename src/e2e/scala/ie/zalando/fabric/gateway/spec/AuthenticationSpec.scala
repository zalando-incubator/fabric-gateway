package ie.zalando.fabric.gateway.spec

import com.softwaremill.sttp._
import ie.zalando.fabric.gateway.{LoggingSttpBackend, TestConstants}
import org.scalatest._

class AuthenticationSpec extends FunSpec with Matchers {

  implicit val backend = new LoggingSttpBackend[Id, Nothing](HttpURLConnectionBackend())

  describe("Fabric Gateway auth functionality") {
    it("should reject requests to undeclared routes with a 404, even if a token is present") {
      val unlistedUri = uri"https://${TestConstants.TestAppHost}/blah/undefined"
      val noAuthResp  = sttp.get(unlistedUri).send()
      val authResp = sttp
        .get(unlistedUri)
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .send()

      noAuthResp.code shouldBe 401
      authResp.code shouldBe 404
      authResp.body match {
        case Left(body) => body shouldBe """{"title":"Gateway Rejected","status":404,"detail":"Gateway Route Not Matched"}"""
        case _          => fail("Expecting an err response")
      }
    }

    it("should reject requests with no token with a 401") {
      val resp = sttp.get(TestConstants.TestAppResourceById(123)).send()

      resp.code shouldBe 401
    }

    it("should reject invalid token with a 401") {
      val resp = sttp
        .get(TestConstants.TestAppResourceById(123))
        .header("Authorization", "Bearer abc123")
        .send()

      resp.code shouldBe 401
    }

    it("should pass through requests which have a valid token") {
      val resp = sttp
        .get(TestConstants.TestAppResourceById(123))
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .send()

      resp.code shouldBe 200
    }

    it("should reject requests which don't have the required scopes") {
      val resp = sttp
        .get(TestConstants.TestAppResources())
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .send()

      resp.code shouldBe 403
    }
  }
}
