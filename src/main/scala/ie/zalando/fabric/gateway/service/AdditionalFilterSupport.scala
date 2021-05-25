package ie.zalando.fabric.gateway.service

import ie.zalando.fabric.gateway.config.AppConfig
import ie.zalando.fabric.gateway.models.SynchDomain.{DynamicFilter, GatewayMeta, SkipperRouteDefinition}

object AdditionalFilterSupport {
  val AdditionalFiltersAnnotationName = "fabric/additional-filters"

  def enrichRoutesWithAdditionalFilters(routes: List[SkipperRouteDefinition], meta: GatewayMeta): List[SkipperRouteDefinition] = {
    val additionalFilters = extractAdditionalFiltersFromMeta(meta)
    if (additionalFilters.nonEmpty) {
      routes.map(addAdditionalRoutes(_, additionalFilters))
    } else routes
  }

  def extractAdditionalFiltersFromMeta(meta: GatewayMeta): List[DynamicFilter] = meta.annotations
    .get(AdditionalFiltersAnnotationName).map(splitFilters).getOrElse(Nil).filter(isDynamicFilterAllowed)

  def splitFilters(filters: String): List[DynamicFilter] = filters.split("->").map(_.trim).map(DynamicFilter.apply).toList

  def addAdditionalRoutes(route: SkipperRouteDefinition, additionalFilters: List[DynamicFilter]): SkipperRouteDefinition = {
    if (route.customRoute.isDefined) route
    else {
      route.copy(filters = route.filters ::: additionalFilters)
    }
  }

  def isDynamicFilterAllowed(filter: DynamicFilter): Boolean = {
    AppConfig.appConfig.allowedFilters.contains(filter.filterName)
  }
}
