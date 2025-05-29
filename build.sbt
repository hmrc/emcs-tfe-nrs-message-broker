import uk.gov.hmrc.DefaultBuildSettings

lazy val appName: String = "emcs-tfe-nrs-message-broker"

ThisBuild / scalaVersion := "2.13.16"
ThisBuild / majorVersion := 1

lazy val microservice = (project in file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(inConfig(Test)(testSettings) *)
  .settings(
    name := appName,
    libraryDependencies ++= AppDependencies(),
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
    scalacOptions += "-Wconf:cat=deprecation:w,cat=feature:w,cat=optimizer:w,src=target/.*:s",
    resolvers += Resolver.jcenterRepo,
    PlayKeys.playDefaultPort := 8315,
    Runtime / unmanagedClasspath += baseDirectory.value / "resources"
  )
  .settings(CodeCoverageSettings.settings *)

lazy val testSettings: Seq[Def.Setting[_]] = Seq(
  fork := true,
  unmanagedSourceDirectories += baseDirectory.value / "test-utils",
  Test / javaOptions += "-Dlogger.resource=logback-test.xml",
)

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.it)
