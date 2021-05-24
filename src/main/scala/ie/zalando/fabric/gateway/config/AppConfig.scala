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
case class AppConfig(allowedAnnotations: Set[String], allowedFilters: Set[String])

object AppConfig {

  private val log: Logger = LoggerFactory.getLogger(classOf[AppConfig])

  val tracingConfig: ConfigReader.Result[TracingConfig]  = ConfigSource.default.at("tracing").load
  private val allowedAnnotationsConfig: ConfigReader.Result[String] = ConfigSource.default.at("app.allowed-annotations").load
  private val allowedAdditionalFiltersConfig: ConfigReader.Result[String] = ConfigSource.default.at("app.allowed-additional-filters").load

  val appConfig: config.AppConfig = (allowedAnnotationsConfig, allowedAdditionalFiltersConfig) match {
    case (Right(config1), Right(config2)) =>
      AppConfig(config1.split(",").toSet, config2.split(",").toSet[String].map(_.trim))
    case _ =>
      log.error("Defaulting App Config as we could not deserialize configuration")
      AppConfig(Set.empty, Set.empty)
  }
}
