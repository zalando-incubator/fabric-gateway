import sbt.librarymanagement.Configuration

lazy val akkaHttpVersion    = "10.1.8"
lazy val akkaVersion        = "2.5.22"
lazy val monocleVersion     = "1.5.0"
lazy val scalaTestVersion   = "3.0.5"
lazy val mockitoVersion     = "1.5.17"
lazy val logbackJsonVersion = "0.1.5"

lazy val commonSettings = Seq(
  scalaVersion := "2.12.8",
  name := "fabric-gateway",
  organization := "ie.zalando",
  scalacOptions ++= Seq(
    "-deprecation", // Emit warning and location for usages of deprecated APIs.
    "-encoding",
    "utf-8", // Specify character encoding used by source files.
    "-explaintypes", // Explain type errors in more detail.
    "-feature", // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
    "-language:experimental.macros", // Allow macro definition (besides implementation and application)
    "-language:higherKinds", // Allow higher-kinded types
    "-language:implicitConversions", // Allow definition of implicit functions called views
    "-unchecked", // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
    "-Xfatal-warnings", // Fail the compilation if there are any warnings.
    "-Xfuture", // Turn on future language features.
    "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
    "-Xlint:by-name-right-associative", // By-name parameter of right associative operator.
    "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
    "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
    "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
    "-Xlint:option-implicit", // Option.apply used implicit view.
    "-Xlint:package-object-classes", // Class or object defined in package object.
    "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
    "-Xlint:unsound-match", // Pattern match may not be typesafe.
    "-Yno-adapted-args", // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
    "-Ypartial-unification", // Enable partial unification in type constructor inference
    "-Ywarn-dead-code", // Warn when dead code is identified.
    "-Ywarn-extra-implicit", // Warn when more than one implicit parameter section is defined.
    "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
    "-Ywarn-infer-any", // Warn when a type argument is inferred to be `Any`.
    "-Ywarn-nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Ywarn-nullary-unit", // Warn when nullary methods return Unit.
    "-Ywarn-numeric-widen", // Warn when numerics are widened.
    "-Ywarn-unused:implicits", // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports", // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals", // Warn if a local definition is unused.
    "-Ywarn-unused:params", // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars", // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates", // Warn if a private member is unused.
    "-Ywarn-value-discard" // Warn when non-Unit expression results are unused.
  )
)

fork in IntegrationTest := true
envVars in IntegrationTest := Map("SKUBER_URL" -> "http://localhost:8001")

test := Seq(
  (test in Test).value,
  (test in IntegrationTest).value
)

val EndToEndTest = Configuration.of("End2EndTest", "e2e") extend (Test)

lazy val e2eSettings =
  inConfig(EndToEndTest)(Defaults.testSettings) ++
    Seq(fork in EndToEndTest := false,
        parallelExecution in EndToEndTest := false,
        scalaSource in EndToEndTest := baseDirectory.value / "src/e2e/scala")

lazy val licenseSettings = Seq(
  licenseReportTitle := "GatewayOperatorDepLicenses",
  licenseConfigurations += "compile",
  licenseReportTypes := Seq(MarkDown)
)

lazy val buildVersion = sys.env.getOrElse("CDP_BUILD_VERSION", "local")

lazy val root = (project in file("."))
  .configs(IntegrationTest, EndToEndTest)
  .settings(
    commonSettings,
    Defaults.itSettings,
    e2eSettings,
    parallelExecution in IntegrationTest := false,
    libraryDependencies ++= Seq(
      // Service Deps
      "com.typesafe.akka"          %% "akka-http"             % akkaHttpVersion,
      "com.typesafe.akka"          %% "akka-stream"           % akkaVersion,
      "com.typesafe.akka"          %% "akka-slf4j"            % akkaVersion,
      "de.heikoseeberger"          %% "akka-http-circe"       % "1.25.2",
      "ch.qos.logback"             % "logback-classic"        % "1.2.3",
      "com.fasterxml.jackson.core" % "jackson-databind"       % "2.9.8",
      "ch.qos.logback.contrib"     % "logback-json-classic"   % logbackJsonVersion,
      "ch.qos.logback.contrib"     % "logback-jackson"        % logbackJsonVersion,
      "com.github.julien-truffaut" %% "monocle-core"          % monocleVersion,
      "com.github.julien-truffaut" %% "monocle-macro"         % monocleVersion,
      "io.skuber"                  %% "skuber"                % "2.3.0",
      "io.opentracing.contrib"     % "opentracing-scala-akka" % "0.1.0",
      "com.lightstep.tracer"       % "lightstep-tracer-jre"   % "0.16.4",
      "com.lightstep.tracer"       % "tracer-okhttp"          % "0.17.2",
      "com.github.pureconfig"      %% "pureconfig"            % "0.12.2",

      // Test Deps
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
      "org.mockito"   %% "mockito-scala-scalatest" % mockitoVersion % Test,

      // Integration Test Deps
      "org.scalatest"          %% "scalatest"               % scalaTestVersion % IntegrationTest,
      "org.mockito"            %% "mockito-scala-scalatest" % mockitoVersion   % IntegrationTest,
      "com.typesafe.akka"      %% "akka-stream-testkit"     % akkaVersion      % IntegrationTest,
      "com.typesafe.akka"      %% "akka-http-testkit"       % akkaHttpVersion  % IntegrationTest,
      "com.github.tomakehurst" %  "wiremock-jre8"           % "2.23.2"         % IntegrationTest,

      // End 2 End Test Deps
      "com.softwaremill.sttp" %% "core" % "1.3.5" % EndToEndTest
    )
  )
  .settings(licenseSettings)
