package ie.zalando.fabric.gateway

import com.softwaremill.sttp.{MonadError, Request, Response, SttpBackend}
import org.slf4j.LoggerFactory

class LoggingSttpBackend[R[_], S](delegate: SttpBackend[R, S]) extends SttpBackend[R, S] {

  val logger = LoggerFactory.getLogger("sttp-request-logger")

  override def send[T](request: Request[T, S]): R[Response[T]] = {
    responseMonad.map(responseMonad.handleError(delegate.send(request)) {
      case e: Exception =>
        logger.error(s"Exception when sending request: $request", e)
        responseMonad.error(e)
    }) { response =>
      if (response.isSuccess) {
        logger.info(s"For request: $request got response: $response")
      } else {
        logger.warn(s"For request: $request got response: $response")
      }
      response
    }
  }
  override def close(): Unit                = delegate.close()
  override def responseMonad: MonadError[R] = delegate.responseMonad
}
