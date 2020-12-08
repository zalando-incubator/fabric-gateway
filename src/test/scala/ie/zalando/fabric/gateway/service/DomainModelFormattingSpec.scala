package ie.zalando.fabric.gateway.service

import cats.data.{NonEmptyList => NEL}
import ie.zalando.fabric.gateway.models.SynchDomain._
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FlatSpec, Matchers}

class DomainModelFormattingSpec extends FlatSpec with Matchers {

  "Path Substitutions" should "format to valid skipper filter Path predicates" in {
    val testData = Table(
      ("input", "expectedSkipper"),
      ("/test/*", """Path("/test/:id")"""),
      ("/test/*/blah", """Path("/test/:id/blah")"""),
      ("/test/", """Path("/test/")"""),
      ("/test", """Path("/test")"""),
      ("/test/id/whatever", """Path("/test/id/whatever")"""),
      ("/test/{param}", """Path("/test/:param")"""),
      ("/test/{param}/", """Path("/test/:param/")"""),
      ("/test/{param}/sub", """Path("/test/:param/sub")"""),
      ("/test/{param}/sub/{sub-param}", """Path("/test/:param/sub/:sub-param")"""),
      ("/test/{param}/sub/{sub-param}/", """Path("/test/:param/sub/:sub-param/")"""),
      ("/test/{param}/sub/*", """Path("/test/:param/sub/:id")"""),
      ("/test/*/sub/*", """Path("/test/:id/sub/:id")"""),
      ("/test/*id/sub/**", """Path("/test/:id/sub/**")"""),
      ("/test/*id-one/sub/{sub-param}/child/*wildcard", """Path("/test/:id-one/sub/:sub-param/child/:wildcard")"""),
      ("/**", """PathSubtree("/")""")
    )

    forAll(testData) { (input: String, expectedSkipperPath: String) =>
      PathMatch.formatAsSkipperPath(input) shouldBe expectedSkipperPath
    }
  }

  "UID Matching" should "format to a valid skipper Token Introspection predicate" in {
    val testData = Table(
      ("input", "expectedPredicate"),
      (NEL.one("Name"), """JWTPayloadAnyKV("https://identity.zalando.com/managed-id", "Name")"""),
      (NEL.of("Name1", "Name2"),
       """JWTPayloadAnyKV("https://identity.zalando.com/managed-id", "Name1", "https://identity.zalando.com/managed-id", "Name2")""")
    )

    forAll(testData) { (input: NEL[String], expectedSkipperPredicate: String) =>
      UidMatch(input).skipperStringValue shouldBe expectedSkipperPredicate
    }
  }

  "InlineContent" should "escape any quotation marks in the body" in {
    val alreadyEscaped = InlineContent("""{\"title\": \"Service down for maintenance\", \"status\":503}""".stripMargin)
    val unescaped      = InlineContent("""{"title": "Service down for maintenance", "status":503}""".stripMargin)
    val expected       = """inlineContent("{\"title\": \"Service down for maintenance\", \"status\":503}")""".stripMargin
    alreadyEscaped.skipperStringValue shouldBe expected
    unescaped.skipperStringValue shouldBe expected
  }
}
