package ie.zalando.fabric.gateway.util

import ie.zalando.fabric.gateway.util.Util.escapeQuotes
import org.scalatest.{FlatSpec, Matchers}

class UtilSpec extends FlatSpec with Matchers {
  "escapeQuotes" should "escape any quotation marks in the string" in {
    val alreadyEscaped = """{\"title\": \"Service down for maintenance\", \"status\":503}"""
    val unescaped      = """{"title": "Service down for maintenance", "status":503}"""
    escapeQuotes(alreadyEscaped) shouldBe alreadyEscaped
    escapeQuotes(unescaped) shouldBe alreadyEscaped
  }
}
