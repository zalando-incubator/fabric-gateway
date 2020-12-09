package ie.zalando.fabric.gateway.service

import cats.data.{NonEmptyList => NEL}
import ie.zalando.fabric.gateway.models.SynchDomain._
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FlatSpec, Matchers}

class SkipperConfigSpec extends FlatSpec with Matchers {

  "Skipper Route Formatting" should "return a None when no predicates are passed" in {
    SkipperConfig.predicatesInSkipperFormat(Nil) shouldBe None
  }

  it should "return a valid skipper route when some predicates are passed" in {
    val routeDefinitions = Table(
      ("inputList", "route"),
      (NEL.of(PathMatch("/api/resource")), """Path("/api/resource")"""),
      (NEL.of(MethodMatch(Get)), """Method("GET")"""),
      (NEL.of(UidMatch(NEL.one("jdoe"))), """JWTPayloadAnyKV("https://identity.zalando.com/managed-id", "jdoe")"""),
      (NEL.of(PathMatch("/api/resource"), MethodMatch(Delete), UidMatch(NEL.one("jdoe"))),
       """Path("/api/resource") && Method("DELETE") && JWTPayloadAnyKV("https://identity.zalando.com/managed-id", "jdoe")""")
    )

    forAll(routeDefinitions) { (input, output) =>
      SkipperConfig.predicatesInSkipperFormat(input) shouldBe output
    }
  }

  it should "return a None when no filters are passed" in {
    SkipperConfig.filtersInSkipperFormat(Nil) shouldBe None
  }

  it should "return a valid skipper route when some filters are passed" in {
    val routeDefinitions = Table(
      ("inputList", "route"),
      (NEL.of(FlowId), """flowId("reuse")"""),
      (NEL.of(RequiredPrivileges(NEL.of("a", "b"))), """oauthTokeninfoAllScope("a", "b")"""),
      (NEL.of(GlobalRouteRateLimit("testGW", PathMatch("/api"), MethodMatch(Get), 25, PerMinute)),
       """inlineContentIfStatus(429, "{\"title\": \"Rate limit exceeded\", \"detail\": \"See the x-rate-limit header for your rate limit per minute, and the retry-after header for how many seconds to wait before retrying.\", \"status\": 429}", "application/problem+json") -> clusterClientRatelimit("testGW_api_GET", 25, "1m", "Authorization")"""),
      (NEL.of(GlobalRouteRateLimit("testGW", PathMatch("/api"), MethodMatch(Get), 15, PerHour)),
       """inlineContentIfStatus(429, "{\"title\": \"Rate limit exceeded\", \"detail\": \"See the x-rate-limit header for your rate limit per minute, and the retry-after header for how many seconds to wait before retrying.\", \"status\": 429}", "application/problem+json") -> clusterClientRatelimit("testGW_api_GET", 15, "1h", "Authorization")"""),
      (NEL.of(GlobalRouteRateLimit("testGW", PathMatch("/api"), MethodMatch(Get), 15, PerHour), FlowId),
       """inlineContentIfStatus(429, "{\"title\": \"Rate limit exceeded\", \"detail\": \"See the x-rate-limit header for your rate limit per minute, and the retry-after header for how many seconds to wait before retrying.\", \"status\": 429}", "application/problem+json") -> clusterClientRatelimit("testGW_api_GET", 15, "1h", "Authorization") -> flowId("reuse")"""),
      (NEL.of(ClientSpecificRouteRateLimit("testGW", PathMatch("/api"), MethodMatch(Get), ClientMatch("svc"), 25, PerMinute)),
       """inlineContentIfStatus(429, "{\"title\": \"Rate limit exceeded\", \"detail\": \"See the x-rate-limit header for your rate limit per minute, and the retry-after header for how many seconds to wait before retrying.\", \"status\": 429}", "application/problem+json") -> clusterClientRatelimit("testGW_api_GET_svc", 25, "1m", "Authorization")"""),
      (NEL.of(ClientSpecificRouteRateLimit("testGW", PathMatch("/api"), MethodMatch(Get), ClientMatch("svc"), 15, PerHour)),
       """inlineContentIfStatus(429, "{\"title\": \"Rate limit exceeded\", \"detail\": \"See the x-rate-limit header for your rate limit per minute, and the retry-after header for how many seconds to wait before retrying.\", \"status\": 429}", "application/problem+json") -> clusterClientRatelimit("testGW_api_GET_svc", 15, "1h", "Authorization")"""),
      (NEL.of(ClientSpecificRouteRateLimit("testGW", PathMatch("/api"), MethodMatch(Get), ClientMatch("svc"), 15, PerHour),
              FlowId),
       """inlineContentIfStatus(429, "{\"title\": \"Rate limit exceeded\", \"detail\": \"See the x-rate-limit header for your rate limit per minute, and the retry-after header for how many seconds to wait before retrying.\", \"status\": 429}", "application/problem+json") -> clusterClientRatelimit("testGW_api_GET_svc", 15, "1h", "Authorization") -> flowId("reuse")"""),
      (NEL.of(RequiredPrivileges(NEL.of("c")),
              GlobalRouteRateLimit("testGW", PathMatch("/api"), MethodMatch(Get), 38, PerMinute)),
       """oauthTokeninfoAllScope("c") -> inlineContentIfStatus(429, "{\"title\": \"Rate limit exceeded\", \"detail\": \"See the x-rate-limit header for your rate limit per minute, and the retry-after header for how many seconds to wait before retrying.\", \"status\": 429}", "application/problem+json") -> clusterClientRatelimit("testGW_api_GET", 38, "1m", "Authorization")"""),
      (NEL.of(RequiredPrivileges(NEL.of("c")),
              GlobalRouteRateLimit("testGW", PathMatch("/api"), MethodMatch(Get), 38, PerMinute),
              FlowId),
       """oauthTokeninfoAllScope("c") -> inlineContentIfStatus(429, "{\"title\": \"Rate limit exceeded\", \"detail\": \"See the x-rate-limit header for your rate limit per minute, and the retry-after header for how many seconds to wait before retrying.\", \"status\": 429}", "application/problem+json") -> clusterClientRatelimit("testGW_api_GET", 38, "1m", "Authorization") -> flowId("reuse")""")
    )

    forAll(routeDefinitions) { (input, output) =>
      val actual = SkipperConfig.filtersInSkipperFormat(input)
      actual shouldBe output
    }
  }

  it should "correct bridge the predicate and filter formats" in {
    val customRoute = SkipperCustomRoute(
      NEL.of(PathMatch("/api/resource"), MethodMatch(Delete)),
      NEL.of(RequiredPrivileges(NEL.of("c")),
             GlobalRouteRateLimit("testGW", PathMatch("/api"), MethodMatch(Get), 15, PerMinute),
             FlowId)
    )

    val expected =
      """Path("/api/resource") && Method("DELETE") -> oauthTokeninfoAllScope("c") -> inlineContentIfStatus(429, "{\"title\": \"Rate limit exceeded\", \"detail\": \"See the x-rate-limit header for your rate limit per minute, and the retry-after header for how many seconds to wait before retrying.\", \"status\": 429}", "application/problem+json") -> clusterClientRatelimit("testGW_api_GET", 15, "1m", "Authorization") -> flowId("reuse")"""
    SkipperConfig.customRouteInSkipperFormat(customRoute) shouldBe expected
  }

  "EnableAccessLog" should "should display the response code masks when passed in" in {
    val routeDefinitions = Table(
      ("input", "filter"),
      (EnableAccessLog(List(1, 310, 20)), "enableAccessLog(1, 310, 20)"),
      (EnableAccessLog(Nil), "enableAccessLog()")
    )

    forAll(routeDefinitions) { (input, output) =>
      input.skipperStringValue shouldBe output
    }
  }
}
