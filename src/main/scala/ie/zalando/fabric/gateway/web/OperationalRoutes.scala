package ie.zalando.fabric.gateway.web

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.RouteDirectives.complete

trait OperationalRoutes {

  implicit def system: ActorSystem

  lazy val operationalRoutes: Route = (path("health") & get) {
    complete(StatusCodes.OK)
  }
}
