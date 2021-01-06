package ie.zalando.fabric.gateway

import com.softwaremill.sttp.{Id, RequestT}
import ie.zalando.fabric.gateway.model.Model.SimpleResponse

import scala.annotation.tailrec

package object spec {
  @tailrec
  final def runReqs(i: Int, results: List[SimpleResponse] = Nil)(implicit req: RequestT[Id, String, Nothing],
                                                                 backend: LoggingSttpBackend[Id, Nothing]): List[SimpleResponse] =
    i match {
      case 0 => results
      case _ =>
        val res = req.send()
        val body = res.rawErrorBody match {
          case Left(v)  => new String(v)
          case Right(v) => v
        }
        runReqs(i - 1, SimpleResponse(res.code, body, res.headers.toMap) :: results)
    }

  def getHeader(headerKey: String, headers: Map[String, String]): Option[String] =
    headers.find(_._1.equalsIgnoreCase(headerKey)).map(_._2)
}
