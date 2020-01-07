package ie.zalando.fabric.gateway.config

import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.generic.auto._

object AppConfig {

  val tracingConfig: ConfigReader.Result[TracingConfig] = ConfigSource.default.at("tracing").load
}
