package ie.zalando.fabric.gateway.web

import java.nio.file.{Files, Paths}
import java.security.{KeyStore, SecureRandom}
import java.util.Base64

import akka.http.scaladsl.{ConnectionContext, HttpsConnectionContext}
import ie.zalando.fabric.gateway.features.UseUnsafeDefaultKeystore
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import org.slf4j.{Logger, LoggerFactory}

trait HttpsContext {

  val logger: Logger = LoggerFactory.getLogger(getClass)

  // Env Vars
  val JksPassword = "WEBHOOK_TLS_JKS_PASSWORD"
  val JksLocation = "WEBHOOK_TLS_JKS_FILE_LOCATION"

  // Defaults
  val DefaultBase64EncodedKeystorePassword = "c3RlbkZybktINA=="

  lazy val httpsCtxt: HttpsConnectionContext = {
    val jksPassword: String = sys.env.getOrElse(JksPassword, DefaultBase64EncodedKeystorePassword)

    val pwd = new String(Base64.getDecoder.decode(jksPassword)).toCharArray

    val ks: KeyStore = KeyStore.getInstance("PKCS12")

    val keystore = UseUnsafeDefaultKeystore
      .runIfEnabled { () =>
        getClass.getClassLoader.getResourceAsStream("ssl/gateway-operator.jks")
      }
      .getOrElse {
        sys.env
          .get(JksLocation)
          .map { fileLocation =>
            logger.info("Attempting to use Keystore from {}", fileLocation)
            Files.newInputStream(Paths.get(fileLocation))
          }
          .getOrElse {
            throw new IllegalStateException(
              s"ENV Var $JksLocation needs to be populated or you need to enable ${UseUnsafeDefaultKeystore.envName}")
          }
      }

    ks.load(keystore, pwd)

    val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, pwd)

    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    tmf.init(ks)

    val sslContext: SSLContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)

    ConnectionContext.https(sslContext)
  }
}
