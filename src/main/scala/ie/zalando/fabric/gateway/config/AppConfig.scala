package ie.zalando.fabric.gateway.config

import java.net.URI

import ie.zalando.fabric.gateway.config
import org.slf4j.{Logger, LoggerFactory}
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.generic.auto._

case class TracingConfig(accessToken: String,
                         collectorHost: URI,
                         collectorPort: Int,
                         componentName: String,
                         disableTracing: Option[Boolean])
case class AppConfig(allowedAnnotations: Set[String])

object AppConfig {

  private val log: Logger = LoggerFactory.getLogger(classOf[AppConfig])

  val tracingConfig: ConfigReader.Result[TracingConfig]  = ConfigSource.default.at("tratocing").load
  private val appPureConfig: ConfigReader.Result[String] = ConfigSource.default.at("app.allowed-annotations").load

  val appConfig: config.AppConfig = appPureConfig match {
    case Left(failures) =>
      log.error(s"Defaulting App Config as we could not deserialize configuration due to $failures")
      AppConfig(Set.empty)
    case Right(config) =>
      AppConfig(config.split(",").toSet)
  }
}
