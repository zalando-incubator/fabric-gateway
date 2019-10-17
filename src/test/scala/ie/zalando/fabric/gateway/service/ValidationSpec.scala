package ie.zalando.fabric.gateway.service

import cats.data.Validated.{Invalid, Valid}
import ie.zalando.fabric.gateway.models.HttpModels.ValidationRequest
import ie.zalando.fabric.gateway.models.ValidationDomain.ResourceDetails
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FlatSpec, Matchers}

class ValidationSpec extends FlatSpec with Matchers {

  "Paths" should "fail the path count validation if they have less than 1 path" in {
    ResourcePersistenceValidations.validatePathsNonEmpty(ResourceDetails(Nil)) match {
      case Valid(_) => fail("Should not have passed validation")
      case Invalid(nel) =>
        nel.size shouldBe 1
        nel.head.errorMessage shouldBe "There must be at least 1 path defined"
    }
  }

  it should "pass the path count validation when there are any path entries" in {
    ResourcePersistenceValidations.validatePathsNonEmpty(ResourceDetails("/whatever" :: Nil)) match {
      case Invalid(_) => fail("Should not have failed validation")
      case Valid(rd)  => rd.paths.head shouldBe "/whatever"
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

  it should "should return all errors" in {
    val vr = ValidationRequest(
      uid = "uid",
      name = "name",
      namespace = "namespace",
      resource = ResourceDetails(
        List(
          "/happy/path",
          "/sad/**/path",
          "/**/another/sad/path"
        )),
      hasExternallyManagedServices = false,
      definedServiceCount = 1
    )
    val decision = ResourcePersistenceValidations.isValid(vr)
    decision.rejected shouldBe true
    decision.reasons.size shouldBe 2
    decision.reasons.map(_.reason) shouldBe List(
      "/sad/**/path is invalid. A Path can only contain `**` as the last element in the path",
      "/**/another/sad/path is invalid. A Path can only contain `**` as the last element in the path"
    )
  }
}
