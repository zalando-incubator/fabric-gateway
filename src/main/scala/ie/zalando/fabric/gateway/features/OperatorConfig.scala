package ie.zalando.fabric.gateway.features

object OperatorConfig {
  def versionedHostsBase: Option[String] = sys.env.get("VERSIONED_HOSTS_BASE_DOMAIN").map(_.toLowerCase.trim)
}
