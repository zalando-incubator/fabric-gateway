package ie.zalando.fabric.gateway.model

object Model {
  case class SimpleResponse(status: Int, body: String, headers: Map[String, String])
}
