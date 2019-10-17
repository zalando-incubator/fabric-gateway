package ie.zalando.fabric.gateway.service

import cats.data.NonEmptyList
import ie.zalando.fabric.gateway.models.SynchDomain.{SkipperCustomRoute, SkipperFilter, SkipperPredicate}

object SkipperConfig {

  def predicatesInSkipperFormat(predicates: List[SkipperPredicate]): Option[String] = predicates match {
    case Nil        => None
    case hd :: tail => Some(predicatesInSkipperFormat(NonEmptyList(hd, tail)))
  }

  def predicatesInSkipperFormat(predicates: NonEmptyList[SkipperPredicate]): String =
    predicates.map(_.skipperStringValue()).toList.mkString(predicates.head.divider())

  def filtersInSkipperFormat(filters: List[SkipperFilter]): Option[String] = filters match {
    case Nil        => None
    case hd :: tail => Some(filtersInSkipperFormat(NonEmptyList(hd, tail)))
  }

  def filtersInSkipperFormat(filters: NonEmptyList[SkipperFilter]): String =
    filters.map(_.skipperStringValue()).toList.mkString(filters.head.divider())

  def customRouteInSkipperFormat(route: SkipperCustomRoute): String =
    predicatesInSkipperFormat(route.predicates) +
      route.filters.head.divider() +
      filtersInSkipperFormat(route.filters)
}
