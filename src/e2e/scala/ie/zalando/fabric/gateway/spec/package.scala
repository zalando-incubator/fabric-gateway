package ie.zalando.fabric.gateway

import com.softwaremill.sttp.{Id, RequestT}

import scala.annotation.tailrec

package object spec {
  @tailrec
  final def runReqs(i: Int, results: List[Int] = Nil)(implicit req: RequestT[Id, String, Nothing],
                                                      backend: LoggingSttpBackend[Id, Nothing]): List[Int] = i match {
    case 0 => results
    case _ => runReqs(i - 1, req.send().code :: results)
  }
}
