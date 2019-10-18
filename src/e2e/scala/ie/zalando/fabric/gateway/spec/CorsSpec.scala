package ie.zalando.fabric.gateway.spec

import com.softwaremill.sttp._
import ie.zalando.fabric.gateway.TestConstants._
import ie.zalando.fabric.gateway.{LoggingSttpBackend, TestConstants}
import org.scalatest._

class CorsSpec extends FunSpec with Matchers {

  implicit val backend = new LoggingSttpBackend[Id, Nothing](HttpURLConnectionBackend())

  describe("Fabric Gateway Cors Support") {
    it("should allow unauthenticated options requests when cors is configured") {
      val origin1 = sttp
        .options(TestConstants.TestAppResourceById(123))
        .header("Origin", "https://example.com")
        .send()

      val origin2 = sttp
        .options(TestConstants.TestAppResourceById(123))
        .header("Origin", "https://example-other.com")
        .send()

      val notAllowed = sttp
        .options(TestConstants.TestAppResourceById(123))
        .header("Origin", "https://example-not-allowed.com")
        .send()

      origin1.code shouldBe 204
      origin2.code shouldBe 204
      notAllowed.code shouldBe 204

      origin1.headers should contain allOf (
        "Access-Control-Allow-Methods"     -> "GET, PUT, OPTIONS",
        "Access-Control-Allow-Credentials" -> "true",
        "Access-Control-Allow-Headers"     -> "X-Flow-Id, Content-Type",
        "Access-Control-Allow-Origin"      -> "https://example.com",
      )
      origin2.headers should contain allOf (
        "Access-Control-Allow-Methods"     -> "GET, PUT, OPTIONS",
        "Access-Control-Allow-Credentials" -> "true",
        "Access-Control-Allow-Headers"     -> "X-Flow-Id, Content-Type",
        "Access-Control-Allow-Origin"      -> "https://example-other.com",
      )
      notAllowed.headers should contain allOf (
        "Access-Control-Allow-Methods"     -> "GET, PUT, OPTIONS",
        "Access-Control-Allow-Credentials" -> "true",
        "Access-Control-Allow-Headers"     -> "X-Flow-Id, Content-Type"
      )
      notAllowed.headers.map(_._1) should not contain ("Access-Control-Allow-Origin")
    }

    it("should not allow unauthenticated options requests when cors is not configured") {
      val resp = sttp
        .options(TestConstants.TestAppResourceById(123, WhitelistTestHost))
        .send()

      resp.code shouldBe 401
    }
  }
}