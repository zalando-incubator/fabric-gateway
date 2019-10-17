package ie.zalando.fabric.gateway.web.marshalling

import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.util.ByteString
import de.heikoseeberger.akkahttpcirce.BaseCirceSupport
import io.circe.{Decoder, Json, jawn}
import org.slf4j.{Logger, LoggerFactory}

trait VerboseLoggingUnmarshalling extends BaseCirceSupport {

  this: BaseCirceSupport =>

  private val logger: Logger = LoggerFactory.getLogger("RequestUnmarshaller")

  implicit final val entityLoggedJsonUnmarshaller: FromEntityUnmarshaller[Json] =
    Unmarshaller.byteStringUnmarshaller
      .forContentTypes(unmarshallerContentTypes: _*)
      .map {
        case ByteString.empty => throw Unmarshaller.NoContentException
        case data =>
          logger.debug("Unmarshalling entity request: {}", data.utf8String)
          jawn.parseByteBuffer(data.asByteBuffer).fold(throw _, identity)
      }

  override implicit final def unmarshaller[A: Decoder]: FromEntityUnmarshaller[A] = {
    def decode(json: Json) = Decoder[A].decodeJson(json).fold(throw _, identity)
    entityLoggedJsonUnmarshaller.map(decode)
  }
}

object VerboseLoggingUnmarshalling extends VerboseLoggingUnmarshalling
