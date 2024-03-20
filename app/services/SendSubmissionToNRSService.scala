/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import config.AppConfig
import models.mongo.MongoLockResponses
import scheduler.{JobFailed, ScheduledService}
import uk.gov.hmrc.mongo.lock.{LockRepository, LockService, MongoLockRepository}
import utils.PagerDutyHelper.PagerDutyKeys.MONGO_LOCK_UNKNOWN_EXCEPTION
import utils.{Logging, PagerDutyHelper}

import javax.inject.Inject
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future}

class SendSubmissionToNRSService @Inject()(lockRepositoryProvider: MongoLockRepository,
                                           appConfig: AppConfig
                                          )(implicit ec: ExecutionContext) extends ScheduledService[Either[JobFailed, String]] with Logging {

  val jobName = "SendSubmissionToNRSJob"
  lazy val mongoLockTimeoutSeconds: Int = appConfig.getMongoLockTimeoutForJob(jobName)

  lazy val lockKeeper: LockService = new LockService {
    override val lockId: String = s"schedules.$jobName"
    override val ttl: Duration = mongoLockTimeoutSeconds.seconds
    override val lockRepository: LockRepository = lockRepositoryProvider
  }

  //scalastyle:off
  override def invoke: Future[Either[JobFailed, String]] = {
    tryLock {
      logger.info(s"[$jobName][invoke] - Job started")
      //TODO: implement getting all pending or failed records and sending them to NRS
      Future(Right(""))
    }
  }

  def tryLock(f: => Future[Either[JobFailed, String]]): Future[Either[JobFailed, String]] = {
    lockKeeper.withLock(f).map {
      case Some(result) => result
      case None =>
        logger.info(s"[$jobName] Locked because it might be running on another instance")
        Right(s"$jobName - JobAlreadyRunning")
    }.recover {
      case e: Exception =>
        PagerDutyHelper.log("tryLock", MONGO_LOCK_UNKNOWN_EXCEPTION)
        logger.warn(s"[$jobName] Failed with exception")
        Left(MongoLockResponses.UnknownException(e))
    }
  }
}
