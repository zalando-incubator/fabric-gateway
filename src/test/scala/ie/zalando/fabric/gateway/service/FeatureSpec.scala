package ie.zalando.fabric.gateway.service

import ie.zalando.fabric.gateway.features.TlsEndpointSupport
import org.scalatest.{FlatSpec, Matchers}

class FeatureSpec extends FlatSpec with Matchers {

  "TLS Feature Flag" should "be enabled if the appropriate env var is set" in {
    setEnv(TlsEndpointSupport.envName, "TRUE")

    TlsEndpointSupport.isFeatureEnabled shouldBe true
    val Some(result) = TlsEndpointSupport.runIfEnabled { () =>
      "executed"
    }
    result shouldBe "executed"
  }

  it should "be disabled if the appropriate env var is not set" in {
    setEnv(TlsEndpointSupport.envName, "anything else")

    TlsEndpointSupport.isFeatureEnabled shouldBe false
    TlsEndpointSupport.runIfEnabled { () =>
      "executed"
    }.isDefined shouldBe false
  }

  private def setEnv(key: String, value: String) = {
    val field = System.getenv().getClass.getDeclaredField("m")
    field.setAccessible(true)
    val map = field.get(System.getenv()).asInstanceOf[java.util.Map[java.lang.String, java.lang.String]]
    map.put(key, value)
  }
}
