package ie.zalando.fabric.gateway.service

import cats.data.NonEmptyList
import ie.zalando.fabric.gateway.models.SynchDomain.{DnsString, DynamicFilter, EmployeeToken, FlowId, GatewayMeta, SkipperCustomRoute, SkipperRouteDefinition}
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.TableDrivenPropertyChecks._

class AdditionalFilterSupportSpec extends FlatSpec with Matchers {

  val TestableGatewayMeta = GatewayMeta(
    name = DnsString.dnsCompliantName("some-dns-name"),
    namespace = "default",
    labels = None,
    annotations = Map(
      "fabric/additional-filters" -> """ fadeIn( "1m", 1.5 )->  dropRequestHeaders("Authorization") -> tee("https://some-backend.org")    """
    ))

  "Dynamic Filters" should "be able to extract the filter name" in {
    val tests = Table(
      ("filterInput", "filterName"),
      ("someFilter", "someFilter"),
      ("""filterWithInput("1m")""", "filterWithInput"),
      ("filterWithMultiInput(1, 2,3)", "filterWithMultiInput"),
      ("filterWeirdSpacing (1, 2, 6)", "filterWeirdSpacing")
    )

    forAll (tests) { (filterInput: String, filterName: String) =>
      DynamicFilter(filterInput).filterName shouldBe filterName
    }
  }

  "Additional Filters" should "be able to parse filters from annotation value" in {
    val tests = Table(
      ("inputString", "parsedFilters"),
      ("singleFilter", List(DynamicFilter("singleFilter"))),
      ("""singleFilterWithInput("1m")""", List(DynamicFilter("""singleFilterWithInput("1m")"""))),
      ("filter1 -> filter2", List(DynamicFilter("filter1"), DynamicFilter("filter2"))),
      ("filter1-> filter2", List(DynamicFilter("filter1"), DynamicFilter("filter2"))),
      ("filter1     ->filter2", List(DynamicFilter("filter1"), DynamicFilter("filter2"))),
      ("filter1(1, 2, 3) -> filter2", List(DynamicFilter("filter1(1, 2, 3)"), DynamicFilter("filter2"))),
      ("filter1(1, 2, 3)->filter2", List(DynamicFilter("filter1(1, 2, 3)"), DynamicFilter("filter2"))),
      ("""filter1(1,2,3)->filter2("1m",2)""", List(DynamicFilter("filter1(1,2,3)"), DynamicFilter("""filter2("1m",2)""")))
    )

    forAll (tests) { (inputString: String, parsedFilters: List[DynamicFilter]) =>
      AdditionalFilterSupport.splitFilters(inputString) shouldBe parsedFilters
    }
  }

  it should "only allow filters in the allow-list" in {
    val tests = Table(
      ("dynamicFilter", "isAllowed"),
      (DynamicFilter("""tee(https://alternate-backend.com)"""), true),
      (DynamicFilter("""fadeIn("3m", 1.5)"""), true),
      (DynamicFilter("""dropRequestHeader("Authorization")"""), false),
      (DynamicFilter("""someRandomUnAllowedFilter"""), false),
    )

    forAll (tests) { (dynamicFilter: DynamicFilter, isAllowed: Boolean) =>
      AdditionalFilterSupport.isDynamicFilterAllowed(dynamicFilter) shouldBe isAllowed
    }
  }

  it should "extract only the allowed dynamic filters from annotations" in {
    val extractedFilterNames = AdditionalFilterSupport.extractAdditionalFiltersFromMeta(TestableGatewayMeta).map(_.filterName)
    extractedFilterNames should contain("fadeIn")
    extractedFilterNames should contain("tee")
    extractedFilterNames.size shouldBe 2
  }

  it should "do a no-op when trying to enrich no routes" in {
    AdditionalFilterSupport.enrichRoutesWithAdditionalFilters(List(), TestableGatewayMeta) shouldBe Nil
  }

  it should "only apply the dynamic filter to non custom routes" in {
    val routes = List(
      SkipperRouteDefinition(
        name = DnsString.dnsCompliantName("route1"),
        predicates = Nil,
        filters = Nil,
        customRoute = Some(SkipperCustomRoute(
          predicates = NonEmptyList.of(EmployeeToken),
          filters = NonEmptyList.of(FlowId)
        )),
        additionalAnnotations = Map.empty
      ),
      SkipperRouteDefinition(
        name = DnsString.dnsCompliantName("route2"),
        predicates = List(EmployeeToken),
        filters = List(FlowId),
        customRoute = None,
        additionalAnnotations = Map.empty
      ),
      SkipperRouteDefinition(
        name = DnsString.dnsCompliantName("route3"),
        predicates = List(EmployeeToken),
        filters = Nil,
        customRoute = None,
        additionalAnnotations = Map.empty
      ),
    )

    val updatedRoutes = AdditionalFilterSupport.enrichRoutesWithAdditionalFilters(routes, TestableGatewayMeta)
    updatedRoutes.size shouldBe 3
    updatedRoutes.filter(_.name.value == "route1").head.filters shouldBe Nil
    updatedRoutes.filter(_.name.value == "route2").head.filters.map(_.skipperStringValue()) shouldBe List(
      """flowId("reuse")""",
      """fadeIn( "1m", 1.5 )""",
      """tee("https://some-backend.org")"""
    )
    updatedRoutes.filter(_.name.value == "route3").head.filters.map(_.skipperStringValue()) shouldBe List(
      """fadeIn( "1m", 1.5 )""",
      """tee("https://some-backend.org")"""
    )
  }
}
