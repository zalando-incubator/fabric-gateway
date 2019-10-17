package ie.zalando.fabric.gateway.spec

import java.security.SecureRandom
import java.security.cert.X509Certificate

import com.softwaremill.sttp._
import ie.zalando.fabric.gateway.{LoggingSttpBackend, TestConstants}
import javax.net.ssl.{HttpsURLConnection, SSLContext, TrustManager, X509TrustManager}
import org.scalatest._

class LegacyTlsValidationSpec extends FunSpec with Matchers {

  implicit val backend = new LoggingSttpBackend[Id, Nothing](
    HttpURLConnectionBackend(
      SttpBackendOptions.Default, { httpURLConnection =>
        val sc: SSLContext = SSLContext.getInstance("TLSv1.1")
        sc.init(
          null,
          Array[TrustManager](new X509TrustManager {
            override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = ()
            override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = ()
            override def getAcceptedIssuers: Array[X509Certificate]                                    = new Array[X509Certificate](0)
          }),
          new SecureRandom
        )
        httpURLConnection.asInstanceOf[HttpsURLConnection].setSSLSocketFactory(sc.getSocketFactory)
      }
    ))

  describe("Gateway needs to be able to support TLS v1.1") {
    it("should pass through requests which have a valid token") {
      val resp = sttp
        .get(TestConstants.TestAppResourceById(123))
        .header("Authorization", s"Bearer ${TestConstants.ValidNonWhitelistedToken}")
        .send()

      resp.code shouldBe 200
    }
  }
}
