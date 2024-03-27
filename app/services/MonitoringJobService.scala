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
import models.mongo.RecordStatusEnum._
import repositories.NRSSubmissionRecordsRepository
import scheduler.{JobFailed, ScheduledService}
import uk.gov.hmrc.mongo.lock.{LockRepository, LockService, MongoLockRepository}
import utils.Logging

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration, DurationInt}

class MonitoringJobService @Inject()(lockRepositoryProvider: MongoLockRepository,
                                     nrsSubmissionRecordsRepository: NRSSubmissionRecordsRepository,
                                     appConfig: AppConfig
                                    )(implicit ec: ExecutionContext) extends ScheduledService[Either[JobFailed, String]] with Logging with Locking {

  val jobName = "MonitoringJob"
  private lazy val mongoLockTimeoutSeconds: Int = appConfig.getMongoLockTimeoutForJob(jobName)

  lazy val lockKeeper: LockService = new LockService {
    override val lockId: String = s"schedules.$jobName"
    override val ttl: Duration = mongoLockTimeoutSeconds.seconds
    override val lockRepository: LockRepository = lockRepositoryProvider
  }

  override def invoke: Future[Either[JobFailed, String]] = {
    tryLock {
      logger.info(s"[$jobName][invoke] - Job started")
      for {
        countOfPendingRecords <- nrsSubmissionRecordsRepository.countRecordsByStatus(PENDING)
        countOfSentRecords <- nrsSubmissionRecordsRepository.countRecordsByStatus(SENT)
        countOfFailedPendingRetryRecords <- nrsSubmissionRecordsRepository.countRecordsByStatus(FAILED_PENDING_RETRY)
        countOfPermanentlyFailedRecords <- nrsSubmissionRecordsRepository.countRecordsByStatus(PERMANENTLY_FAILED)
      } yield {
        val logOfPendingRecordsCount = s"[invoke] - Count of Pending records: $countOfPendingRecords"
        val logOfSentRecordsCount = s"[invoke] - Count of Sent records: $countOfSentRecords"
        val logOfFailedPendingRetryRecordsCount = s"[invoke] - Count of Failed Pending Retry records: $countOfFailedPendingRetryRecords"
        val logOfPermanentlyFailedRecordsCount = s"[invoke] - Count of Permanently Failed records: $countOfPermanentlyFailedRecords"
        val seqOfLogs = Seq(logOfPendingRecordsCount, logOfSentRecordsCount, logOfFailedPendingRetryRecordsCount, logOfPermanentlyFailedRecordsCount)
        seqOfLogs.foreach(logger.info(_))
        Right("Successfully ran monitoring job")
      }
    }
  }
}
