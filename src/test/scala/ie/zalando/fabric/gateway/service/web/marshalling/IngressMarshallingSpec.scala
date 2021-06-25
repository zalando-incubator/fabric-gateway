package ie.zalando.fabric.gateway.service.web.marshalling

import ie.zalando.fabric.gateway.models.SynchDomain.IngressDefinition
import ie.zalando.fabric.gateway.web.marshalling.JsonModels
import io.circe.Json
import io.circe.parser._
import io.circe.syntax._
import org.scalatest.{FlatSpec, Matchers}

class IngressMarshallingSpec extends FlatSpec with Matchers with JsonModels {

  val inputJson: String =
    """
    {
      "apiVersion": "networking.k8s.io/v1",
      "kind": "Ingress",
      "metadata": {
        "annotations": {
           "metacontroller.k8s.io/last-applied-configuration": "{\"apiVersion\":\"networking.k8s.io/v1\",\"kind\":\"Ingress\",\"metadata\":{\"annotations\":{\"zalando.org/aws-load-balancer-ssl-policy\":\"ELBSecurityPolicy-FS-2018-06\",\"zalando.org/skipper-filter\":\"oauthTokeninfoAnyKV(\\\"realm\\\",\n\\\"/services\\\", \\\"realm\\\", \\\"/employees\\\") -\\u003e unverifiedAuditLog(\\\"sub\\\")\n-\\u003e oauthTokeninfoAllScope(\\\"uid\\\", \\\"fabric-demo-app.read\\\") -\\u003e flowId(\\\"reuse\\\")\n-\\u003e forwardToken(\\\"X-TokenInfo-Forward\\\", \\\"uid\\\", \\\"scope\\\", \\\"realm\\\")\n-\\u003e corsOrigin(\\\"https://example.com\\\", \\\"https://example-other.com\\\")\",\"zalando.org/skipper-predicate\":\"Path(\\\"/limited/all\\\")\n\\u0026\\u0026 Method(\\\"GET\\\") \\u0026\\u0026 Header(\\\"X-Forwarded-Proto\\\", \\\"https\\\")\"},\"labels\":{\"controller-uid\":\"d2fbfd57-c3f9-418a-a5ca-69ac0555037f\",\"deployment-id\":\"d-7mwc4teg8kaagfd1umoks5hjc\",\"pipeline-id\":\"l-7djb8yivpng6mt47b4rs4z5go\"},\"name\":\"test-gateway-operator-get-limited-all-all\",\"namespace\":\"fabric\"},\"spec\":{\"rules\":[{\"host\":\"test-gateway-operator.playground.zalan.do\",\"http\":{\"paths\":[{\"backend\":{\"serviceName\":\"gateway-test-app\",\"servicePort\":\"http\"}}]}},{\"host\":\"alt-test-gateway-operator.playground.zalan.do\",\"http\":{\"paths\":[{\"backend\":{\"serviceName\":\"gateway-test-app\",\"servicePort\":\"http\"}}]}}]}}",
           "zalando.org/aws-load-balancer-ssl-policy": "ELBSecurityPolicy-FS-2018-06",
           "zalando.org/skipper-filter": "oauthTokeninfoAnyKV(\"realm\", \"/services\", \"realm\",\n\"/employees\") -> unverifiedAuditLog(\"sub\") -> oauthTokeninfoAllScope(\"uid\",\n\"fabric-demo-app.read\") -> flowId(\"reuse\") -> forwardToken(\"X-TokenInfo-Forward\",\n\"uid\", \"scope\", \"realm\") -> corsOrigin(\"https://example.com\", \"https://example-other.com\")",
           "zalando.org/skipper-predicate": "Path(\"/limited/all\") && Method(\"GET\") && Header(\"X-Forwarded-Proto\",\n\"https\")"
        },
        "labels": {
          "controller-uid": "d2fbfd57-c3f9-418a-a5ca-69ac0555037f",
          "deployment-id": "d-2kwup794dchjgcvrn2avxm8vhj",
          "pipeline-id": "l-cqkha4dknuj26earg41w32ynb"
        },
        "name": "test-gateway-operator-default-404-route",
        "namespace": "fabric"
      },
      "spec": {
        "rules": [
          {
            "host": "test-gateway-operator.playground.zalan.do",
            "http": {
              "paths": [
                {
                  "backend": {
                    "serviceName": "gateway-test-app",
                    "servicePort": "http"
                  },
                  "pathType": "ImplementationSpecific"
                }
              ]
            }
          },
          {
            "host": "alt-test-gateway-operator.playground.zalan.do",
            "http": {
              "paths": [
                {
                  "backend": {
                    "serviceName": "gateway-test-app",
                    "servicePort": "http"
                  },
                  "pathType": "ImplementationSpecific"
                }
              ]
            }
          }
        ]
      }     
    }   
    """

  "Ingress" should "marshal and unmarshal correctly" in {
    decode[IngressDefinition](inputJson) match {
      case Right(inputObject) => {
        val jsonOutput = printer.pretty(inputObject.asJson)
        parseOrFail(jsonOutput) shouldEqual parseOrFail(inputJson)
      }
      case Left(error) =>
        fail(s"Could not decode Ingress Json: ${error.getCause}")
    }
  }

  private def parseOrFail(jsonString: String): Json =
    parse(jsonString).right.getOrElse(fail(s"Could not parse json string: $jsonString"))
}
