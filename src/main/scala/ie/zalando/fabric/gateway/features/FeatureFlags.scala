package ie.zalando.fabric.gateway.features

abstract sealed class FeatureFlag {

  def envName: String
  def isFeatureEnabled: Boolean              = sys.env.get(envName).map(_.toUpperCase.trim).contains("TRUE")
  def runIfEnabled[T](f: () => T): Option[T] = if (isFeatureEnabled) Some(f.apply()) else None
}

object TlsEndpointSupport extends FeatureFlag {
  val envName = "WEBHOOK_TLS_ENABLED"
}

object UseUnsafeDefaultKeystore extends FeatureFlag {
  val envName = "WEBHOOK_TLS_UNSAFE_KEYSTORE_ENABLED"
}

object VersionedHostsEnabled extends FeatureFlag {
  val envName = "VERSIONED_HOSTS_ENABLED"
}