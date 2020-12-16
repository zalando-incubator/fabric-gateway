package ie.zalando.fabric.gateway.web

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import ie.zalando.fabric.gateway.models.HttpModels._
import ie.zalando.fabric.gateway.models.SynchDomain.GatewayStatus
import ie.zalando.fabric.gateway.service.{ResourcePersistenceValidations, ZeroDowntimeIngressTransitions}
import ie.zalando.fabric.gateway.web.marshalling.JsonModels
import ie.zalando.fabric.gateway.web.marshalling.VerboseLoggingUnmarshalling._
import ie.zalando.fabric.gateway.web.tracing.TracingDirectives.trace

import scala.concurrent.ExecutionContext

trait GatewayWebhookRoutes extends JsonModels {

  implicit def system: ActorSystem

  lazy val log = Logging(system, classOf[GatewayWebhookRoutes])

  def createRoutesFromDerivations(ingressTransitions: ZeroDowntimeIngressTransitions): Route =
    pathPrefix("synch") {
      pathEnd {
        post {
          trace("metacontroller_synchronization") { span =>
            withoutSizeLimit {
              entity(as[SynchRequest]) { synchRequest =>
                extractExecutionContext { ec =>
                  implicit val execCtxt: ExecutionContext = ec
                  complete {
                    ingressTransitions
                      .defineSafeRouteTransition(synchRequest.controlledResource.spec,
                        synchRequest.controlledResource.metadata,
                        synchRequest.currentState.values.toSeq,
                        isLegacy = true)
                      .map { ingressDefinitions =>
                        val response = SynchResponse(
                          GatewayStatus(synchRequest.currentState.size, synchRequest.currentState.keySet),
                          ingressDefinitions
                        )
                        log.debug(s"Synch Request response: $response")
                        span.setTag("response_payload", response.toString)
                        response
                      }
                  }
                }
              }
            }
          }
        }
      }
    } ~
      pathPrefix("validate") {
        pathEnd {
          post {
            trace("kubernetes_validation") { _ =>
              withoutSizeLimit {
                entity(as[ValidationRequest]) { validation =>
                  val decision = ResourcePersistenceValidations.isValid(validation)

                  val resp =
                    if (decision.rejected)
                      ValidationResponse(validation.uid, allowed = false, Some(ValidationStatus(decision.reasons)))
                    else
                      ValidationResponse(validation.uid, allowed = true, None)

                  val respWrapper = AdmissionReviewResponseWrapper(response = resp)
                  log.info(s"Validation response for ${validation.name} in ${validation.namespace}: $respWrapper")
                  complete(respWrapper)
                }
              }
            }
          }
        }
      } ~
      pathPrefix("new-synch") {
        pathEnd {
          post {
            trace("metacontroller_synchronization") { span =>
              withoutSizeLimit {
                entity(as[SynchRequest]) { synchRequest =>
                  extractExecutionContext { ec =>
                    implicit val execCtxt: ExecutionContext = ec
                    complete {
                      ingressTransitions
                        .defineSafeRouteTransition(synchRequest.controlledResource.spec,
                          synchRequest.controlledResource.metadata,
                          synchRequest.currentState.values.toSeq,
                          isLegacy = false)
                        .map { ingressDefinitions =>
                          val response = SynchResponse(
                            GatewayStatus(synchRequest.currentState.size, synchRequest.currentState.keySet),
                            ingressDefinitions
                          )
                          log.debug(s"Synch Request response: $response")
                          span.setTag("response_payload", response.toString)
                          response
                        }
                    }
                  }
                }
              }
            }
          }
        }
      }
}
