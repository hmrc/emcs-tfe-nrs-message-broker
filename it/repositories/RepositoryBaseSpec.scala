package repositories

import config.AppConfig
import support.IntegrationSpec
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext

trait RepositoryBaseSpec[A] extends IntegrationSpec
  with DefaultPlayMongoRepositorySupport[A] {

  val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  protected val repository: PlayMongoRepository[A]

  implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
}