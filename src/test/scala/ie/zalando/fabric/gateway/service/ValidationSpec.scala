package ie.zalando.fabric.gateway.service

import cats.data.Validated.{Invalid, Valid}
import ie.zalando.fabric.gateway.models.HttpModels.ValidationRequest
import ie.zalando.fabric.gateway.models.SynchDomain.{Get, Options}
import ie.zalando.fabric.gateway.models.ValidationDomain.{ResourceDetails, ValidationCorsConfig}
import io.circe.{Json, JsonObject}
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FlatSpec, Matchers}

class ValidationSpec extends FlatSpec with Matchers {

  private val EmptyJson = Json.fromJsonObject(JsonObject.empty)

  "Paths" should "fail the path count validation if they have less than 1 path" in {
    ResourcePersistenceValidations.validatePathsNonEmpty(ResourceDetails(Map.empty)) match {
      case Valid(_) => fail("Should not have passed validation")
      case Invalid(nel) =>
        nel.size shouldBe 1
        nel.head.errorMessage shouldBe "There must be at least 1 path defined"
    }
  }

  it should "pass the path count validation when there are any path entries" in {
    val resourceDetails = ResourceDetails(Map("/whatever" -> Map(Get -> EmptyJson)))
    ResourcePersistenceValidations.validatePathsNonEmpty(resourceDetails) match {
      case Invalid(_) => fail("Should not have failed validation")
      case Valid(rd)  => rd shouldBe resourceDetails
    }
  }

  it should "fail the path ** check if there is a path which has ** anywhere but at the end" in {
    val testData = Table(
      ("path", "isValid"),
      ("/test/*", true),
      ("/test/**", true),
      ("/test/*/sub", true),
      ("/test/*/sub/**", true),
      ("/**", true),
      ("/start**", false),
      ("/parent/**/sub", false),
      ("/parent/**/", false),
      ("/parent/**/", false),
      ("/**/sub/*", false),
      ("/parent/{named}/**", true),
      ("/parent/{named}/**/sub", false),
      ("**", false),
    )

    forAll(testData) { (path: String, isValid: Boolean) =>
      ResourcePersistenceValidations.validateStarStarPathPosition(path) match {
        case Valid(_) if !isValid  => fail(s"$path should not have passed validation")
        case Invalid(_) if isValid => fail(s"$path should not have failed validation")
        case Valid(p)              => p shouldBe path
        case Invalid(nel) =>
          nel.size shouldBe 1
          nel.head.errorMessage shouldBe s"$path is invalid. A Path can only contain `**` as the last element in the path"
      }
    }
  }

  "Cors Validation" should "pass when cors is enabled and there are no options verbs" in {
    ResourcePersistenceValidations.validateCorsOptionsRoutes(Some(ValidationCorsConfig(Set.empty)), "/resourceDetails", Get) match {
      case Invalid(_)      => fail("Should not have failed validation")
      case Valid(pathVerb) => pathVerb shouldBe "/resourceDetails:Get"
    }
  }

  it should "fail when cors is enabled and options endpoints are specified" in {
    ResourcePersistenceValidations.validateCorsOptionsRoutes(Some(ValidationCorsConfig(Set.empty)), "/resourceDetails", Options) match {
      case Invalid(nel) =>
        nel.size shouldBe 1
        nel.head.errorMessage shouldBe "You may not define an options endpoint when cors support is configured. Options endpoint found under '/resourceDetails'"
      case Valid(_) => fail("Should have failed validation")
    }
  }

  it should "fail when * is used as an allowed origin" in {
    ResourcePersistenceValidations.validateCorsHostname("*") match {
      case Invalid(nel) =>
        nel.size shouldBe 1
        nel.head.errorMessage shouldBe "You may not use * in your cors allowed origins"
      case Valid(_) => fail("Should have failed")
    }
    ResourcePersistenceValidations.validateCorsHostname("*.example.com") match {
      case Invalid(nel) =>
        nel.size shouldBe 1
        nel.head.errorMessage shouldBe "You may not use * in your cors allowed origins"
      case Valid(_) => fail("Should have failed")
    }
  }

  "DNS Compliance Validation" should "reject name and namespace combinations which exceed length requirements" in {
    ResourcePersistenceValidations.validateNameLength("my-gateway", "my-namespace") match {
      case Valid(concatenatedNameAndSpace) => concatenatedNameAndSpace shouldBe "my-namespace:my-gateway"
      case Invalid(_) => fail
    }


    ResourcePersistenceValidations.validateNameLength("my-stupidly-long-service-name", "my-equally-long-namespace-naming") match {
      case Valid(_) => fail
      case Invalid(nel) =>
        nel.size shouldBe 1
        nel.head.errorMessage shouldBe "Concatenated resource name and namespace length is capped at 60 characters: my-stupidly-long-service-name and my-equally-long-namespace-naming are 61 chars long"
    }
  }

  "Resource Validation" should "should return all errors" in {
    val vr = ValidationRequest(
      uid = "uid",
      name = "name",
      namespace = "namespace",
      resource = ResourceDetails(
        Map(
          "/happy/path"          -> Map(Get -> EmptyJson),
          "/another/happy/path"  -> Map(Get -> EmptyJson, Options -> EmptyJson),
          "/sad/**/path"         -> Map(Get -> EmptyJson),
          "/**/another/sad/path" -> Map(Get -> EmptyJson)
        )),
      hasExternallyManagedServices = false,
      corsConfig = Some(ValidationCorsConfig(Set("exampl^U£^£^&£)&e.com", "*", "example-other.com"))),
      definedServiceCount = 1
    )
    val decision = ResourcePersistenceValidations.isValid(vr)
    decision.rejected shouldBe true
    decision.reasons.size shouldBe 5
    decision.reasons.map(_.reason) shouldBe List(
      "/sad/**/path is invalid. A Path can only contain `**` as the last element in the path",
      "/**/another/sad/path is invalid. A Path can only contain `**` as the last element in the path",
      "The provided cors hostname 'exampl^U£^£^&£)&e.com' is invalid",
      "You may not use * in your cors allowed origins",
      "You may not define an options endpoint when cors support is configured. Options endpoint found under '/another/happy/path'"
    )
  }
}
