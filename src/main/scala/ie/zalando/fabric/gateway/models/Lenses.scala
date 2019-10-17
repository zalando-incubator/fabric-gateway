package ie.zalando.fabric.gateway.models

import ie.zalando.fabric.gateway.models.SynchDomain._
import monocle.function.all._
import monocle.macros.GenLens
import monocle.std.option.some
import monocle.{Lens, Traversal}

object Lenses {

  val paths: Lens[GatewaySpec, GatewayPaths]                               = GenLens[GatewaySpec](_.paths)
  val actions: Lens[PathConfig, GatewayPathRestrictions]                   = GenLens[PathConfig](_.operations)
  val maybeRateLimit: Lens[ActionAuthorizations, Option[RateLimitDetails]] = GenLens[ActionAuthorizations](_.rateLimit)
  val userRateLimits: Lens[RateLimitDetails, Map[String, Int]]             = GenLens[RateLimitDetails](_.uidSpecific)

  val allConfigs: Traversal[GatewaySpec, ActionAuthorizations] =
    paths composeTraversal each composeLens actions composeTraversal each
  val allRateLimits: Traversal[GatewaySpec, Map[String, Int]] =
    allConfigs composeLens maybeRateLimit composePrism some composeLens userRateLimits
}
