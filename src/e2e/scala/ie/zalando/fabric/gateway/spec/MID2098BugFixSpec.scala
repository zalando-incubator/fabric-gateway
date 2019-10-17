package ie.zalando.fabric.gateway.spec

import com.softwaremill.sttp._
import ie.zalando.fabric.gateway.{LoggingSttpBackend, TestConstants}
import org.scalatest._

class MID2098BugFixSpec extends FunSpec with Matchers {

  implicit val backend = new LoggingSttpBackend[Id, Nothing](HttpURLConnectionBackend())

  describe("Paths with only /** were returning a 404") {
    it("should now use a PathSubtree for this edge case so that it no longer returns a 404") {
      val resp = sttp
        .get(TestConstants.TestAppSubResourcesId(TestConstants.MID2098BugTestHost))
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .send()

      resp.code shouldBe 200
    }

    it(
      "should not allow access to a wildcard path at the route level which has a privilege restriction that isnt satisfied in the request") {
      val resp = sttp
        .get(TestConstants.TestAppResources(TestConstants.MID2098BugTestHost))
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .send()

      resp.code shouldBe 403
    }

    it("should not allow access to a path which has a privilege restriction that isnt satisfied in the request") {
      val resp = sttp
        .get(TestConstants.TestAppResourcesId(TestConstants.MID2098BugTestHost))
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .send()

      resp.code shouldBe 403
    }
  }
}
