package ie.zalando.fabric.gateway.spec

import com.softwaremill.sttp._
import ie.zalando.fabric.gateway.{LoggingSttpBackend, TestConstants}
import org.scalatest.{FunSpec, Matchers}

class SmokeSpec extends FunSpec with Matchers {

  implicit val backend = new LoggingSttpBackend[Id, Nothing](HttpURLConnectionBackend())

  describe("A Fabric Gateway") {

    it("should provide a DNS entry for apps which is publicly accessible") {
      val resp = sttp.get(TestConstants.TestAppBaseUri()).send()

      resp.headers.find(_._1 == "Server") shouldBe Some("Server" -> "Skipper")
    }
  }
}
