import sbt.*

object AppDependencies {

  private val hmrcBootstrapVersion = "9.11.0"
  private val hmrcMongoVersion = "2.3.0"
  private val scalamockVersion = "6.0.0"

  val playSuffix = "-play-30"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% s"bootstrap-backend$playSuffix"  % hmrcBootstrapVersion,
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo$playSuffix"         % hmrcMongoVersion,
    "io.github.samueleresca"  %% "pekko-quartz-scheduler"         % "1.2.0-pekko-1.0.x"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% s"bootstrap-test$playSuffix"     % hmrcBootstrapVersion,
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo-test$playSuffix"    % hmrcMongoVersion,
    "org.scalamock"           %%  "scalamock"                     % scalamockVersion
  ).map(_ % Test)

  val it: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% s"bootstrap-test$playSuffix" % hmrcBootstrapVersion % Test
  )

  def apply(): Seq[ModuleID] = compile ++ test

}
