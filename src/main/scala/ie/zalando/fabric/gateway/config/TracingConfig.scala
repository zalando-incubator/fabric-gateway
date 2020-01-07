package ie.zalando.fabric.gateway.config

import java.net.URI

case class TracingConfig(accessToken: String, collectorHost: URI, collectorPort: Int, componentName: String, disableTracing: Option[Boolean])