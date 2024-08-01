import sbt.*

object AppDependencies {

  private val bootstrapVersion = "8.6.0"
  private val hmrcMongoVersion = "2.2.0"
  private val scalamockVersion  =  "6.0.0"

  val playSuffix = "-play-30"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% s"bootstrap-backend$playSuffix"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo$playSuffix"         % hmrcMongoVersion,
    "io.github.samueleresca"  %% "pekko-quartz-scheduler"         % "1.2.0-pekko-1.0.x"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% s"bootstrap-test$playSuffix"     % bootstrapVersion            % "test, it",
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo-test$playSuffix"    % hmrcMongoVersion            % "it",
    "org.scalamock"           %%  "scalamock"                     % scalamockVersion            % "test"
  )
}
