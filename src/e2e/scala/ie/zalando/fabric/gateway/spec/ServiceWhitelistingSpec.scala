package ie.zalando.fabric.gateway.spec

import com.softwaremill.sttp._
import ie.zalando.fabric.gateway.{LoggingSttpBackend, TestConstants}
import org.scalatest._

class ServiceWhitelistingSpec extends FunSpec with Matchers {

  implicit val backend: LoggingSttpBackend[Id, Nothing] = new LoggingSttpBackend[Id, Nothing](HttpURLConnectionBackend())

  describe("Fabric Gateway service white-listing functionality") {
    it("should reject requests from tokens who have the required scopes but are not present in the whitelist") {
      val resp = sttp
        .get(TestConstants.TestAppResources(host = TestConstants.WhitelistTestHost))
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .send()

      resp.code shouldBe 403
    }

    it("should accept requests from valid tokens which are whitelisted and pass the required auth validations") {
      val resp = sttp
        .get(TestConstants.TestAppResources(host = TestConstants.WhitelistTestHost))
        .header("Authorization", s"Bearer ${TestConstants.ValidWhiteListToken}")
        .send()

      resp.code shouldBe 200
    }

    it("should reject requests from valid tokens which are whitelisted but do not pass the required auth validations") {
      val resp = sttp
        .post(TestConstants.TestAppResources(host = TestConstants.WhitelistTestHost))
        .header("Authorization", s"Bearer ${TestConstants.ValidWhiteListToken}")
        .send()

      resp.code shouldBe 403
    }

    it("should reject globally whitelisted token if they are not in a resource specific whitelist") {
      val resp = sttp
        .get(TestConstants.TestAppResourcesId(host = TestConstants.WhitelistTestHost))
        .header("Authorization", s"Bearer ${TestConstants.ValidWhiteListToken}")
        .send()

      resp.code shouldBe 403
    }

    it("should allow resource specific whitelisted tokens") {
      val resp = sttp
        .get(TestConstants.TestAppResourcesId(host = TestConstants.WhitelistTestHost))
        .header("Authorization", s"Bearer ${TestConstants.ValidResourceWhiteListToken}")
        .send()

      resp.code shouldBe 200
    }

    it("should allow resource specific whitelisted tokens at sub resources") {
      val resp = sttp
        .get(TestConstants.TestAppSubResourcesId(host = TestConstants.WhitelistTestHost))
        .header("Authorization", s"Bearer ${TestConstants.ValidResourceWhiteListToken}")
        .send()

      resp.code shouldBe 200
    }

    it("should restrict access only to whitelisted services in a non globally whitelisted gateway which has a whitelisted route") {
      val resp = sttp
        .get(TestConstants.TestAppWhitelistedRoute())
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .send()

      resp.code shouldBe 403
    }

    it("should allow resource specific whitelisted tokens even if there is no global whitelist") {
      val resp = sttp
        .get(TestConstants.TestAppWhitelistedRoute())
        .header("Authorization", s"Bearer ${TestConstants.ValidResourceWhiteListToken}")
        .send()

      resp.code shouldBe 200
    }

    it("should allow non whitelisted tokens to access a resource that has disabled global whitelisting") {
      val resp = sttp
        .get(TestConstants.TestAppOtherResource(TestConstants.WhitelistTestHost))
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .send()

      resp.code shouldBe 200
    }

    it("should prevent all non admin access to a whitelist enabled resource with an empty whitelist") {
      List(TestConstants.ValidNonWhitelistedToken, TestConstants.ValidWhiteListToken, TestConstants.ValidResourceWhiteListToken)
        .foreach { token =>
          sttp
            .post(TestConstants.TestAppOtherResource(TestConstants.WhitelistTestHost))
            .header("Authorization", s"Bearer $token")
            .send()
            .code shouldBe 403
        }
    }
  }
}
