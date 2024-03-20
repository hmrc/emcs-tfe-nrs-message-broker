import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  private val bootstrapVersion = "8.5.0"
  private val hmrcMongoVersion = "1.8.0"
  private val scalamockVersion  =  "5.2.0"

  val playSuffix = "-play-30"

  val compile = Seq(
    "uk.gov.hmrc"             %% s"bootstrap-backend$playSuffix"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo$playSuffix"         % hmrcMongoVersion,
    "io.github.samueleresca"  %% "pekko-quartz-scheduler"         % "1.2.0-pekko-1.0.x"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% s"bootstrap-test$playSuffix"     % bootstrapVersion            % "test, it",
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo-test$playSuffix"    % hmrcMongoVersion            % Test,
    "org.scalamock"           %%  "scalamock"                     % scalamockVersion            % "test"
  )
}
