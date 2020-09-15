package ie.zalando.fabric.gateway

import akka.Done
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.scaladsl.Sink

import scala.concurrent.Future
import scala.io.Source

object TestUtils {

  def synchRequest(payload: String): HttpRequest = HttpRequest(
    method = HttpMethods.POST,
    uri = "/synch",
    entity = HttpEntity(MediaTypes.`application/json`, payload)
  )

  def validationRequest(payload: String): HttpRequest = HttpRequest(
    method = HttpMethods.POST,
    uri = "/validate",
    entity = HttpEntity(MediaTypes.`application/json`, payload)
  )

  def responsePrinter(response: HttpResponse)(implicit mat: Materializer): Future[Done] = {
    response.entity.dataBytes.map(_.utf8String).runWith(Sink.foreach(println))
  }

  object TestData {
    sealed trait ResourcePayload { def payload: String }
    case object ValidSynchRequest extends ResourcePayload {
      def payload: String = Source.fromResource("synch/sampleSynchRequest.json").mkString
    }
    case object ValidSynchRequestWithNamedPathParameters extends ResourcePayload {
      def payload: String = Source.fromResource("synch/sampleSynchRequestWithNamedPathParameters.json").mkString
    }
    case object ValidSynchWithNonDNSCompliantGatewayNameRequest extends ResourcePayload {
      def payload: String = Source.fromResource("synch/sampleSynchRequest.json").mkString
    }
    case object ValidWhitelistSynchRequest extends ResourcePayload {
      def payload: String = Source.fromResource("synch/sampleSynchRequestWithWhitelisting.json").mkString
    }
    case object InvalidRequest extends ResourcePayload {
      def payload: String = Source.fromResource("synch/invalid.json").mkString
    }
    case object ValidValidationRequest extends ResourcePayload {
      def payload: String = Source.fromResource("validate/samplePayload.json").mkString
    }
    case object ValidationRequestForNoPaths extends ResourcePayload {
      def payload: String = Source.fromResource("validate/samplePayloadFailNoPaths.json").mkString
    }
    case object ValidationRequestForInvalidInput extends ResourcePayload {
      def payload: String = Source.fromResource("validate/wronglyFormattedCreatePayload.json").mkString
    }
    case object ValidationRequestForValidStacksetIntegration extends ResourcePayload {
      def payload: String = Source.fromResource("validate/samplePayloadWithStackIntegrationDefined.json").mkString
    }
    case object ValidationRequestForInvalidServiceDefinition extends ResourcePayload {
      def payload: String = Source.fromResource("validate/invalidPayloadWithStackIntegrationAndServiceDefined.json").mkString
    }
    case object ValidSynchRequestWithStackSetManagedServices extends ResourcePayload {
      def payload: String = Source.fromResource("synch/sampleSynchRequestWithStacksetManagedServices.json").mkString
    }
    case object ValidSynchRequestWithNonExistingStackSetManagingServices extends ResourcePayload {
      def payload: String = Source.fromResource("synch/sampleSynchRequestWithNonExistingStacksetManagingServices.json").mkString
    }
    case object ValidSynchRequestWithStackSetManagingServicesButNotTrafficStatus extends ResourcePayload {
      def payload: String =
        Source.fromResource("synch/sampleSynchRequestWithStacksetManagingServicesButNoTrafficStatus.json").mkString
    }
    case object ValidSynchRequestWithStackSetManagingServicesAndNamedPort extends ResourcePayload {
      def payload: String =
        Source.fromResource("synch/sampleSynchRequestWithStacksetNamedPort.json").mkString
    }
    case object ValidSynchRequestWithCorsEnabled extends ResourcePayload {
      def payload: String =
        Source.fromResource("synch/sampleSynchRequestWithCors.json").mkString
    }
    case object BogusStackSetTriggeringRequest extends ResourcePayload {
      def payload: String = Source.fromResource("synch/synchRequestWhichTriggersInvalidStackSetResponse.json").mkString
    }
  }
}
