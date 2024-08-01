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
import connectors.NRSConnector
import models.FailedJobResponses.FailedToProcessRecords
import models.mongo.NRSSubmissionRecord
import models.mongo.RecordStatusEnum.{FAILED_PENDING_RETRY, PERMANENTLY_FAILED, SENT}
import models.response.Downstream4xxError
import repositories.NRSSubmissionRecordsRepository
import scheduler.{JobFailed, ScheduledService}
import uk.gov.hmrc.mongo.lock.{LockRepository, LockService, MongoLockRepository}
import utils.PagerDutyHelper.PagerDutyKeys._
import utils.{Logging, PagerDutyHelper}

import javax.inject.Inject
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future}

class SendSubmissionToNRSService @Inject()(lockRepositoryProvider: MongoLockRepository,
                                           nrsSubmissionRecordsRepository: NRSSubmissionRecordsRepository,
                                           nrsConnector: NRSConnector,
                                           appConfig: AppConfig
                                          )(implicit ec: ExecutionContext) extends ScheduledService[Either[JobFailed, String]] with Logging with Locking {

  val jobName = "SendSubmissionToNRSJob"
  private lazy val mongoLockTimeoutSeconds: Int = appConfig.getMongoLockTimeoutForJob(jobName)

  lazy val lockKeeper: LockService = new LockService {
    override val lockId: String = s"schedules.$jobName"
    override val ttl: Duration = mongoLockTimeoutSeconds.seconds
    override val lockRepository: LockRepository = lockRepositoryProvider
  }

  //scalastyle:off
  override def invoke: Future[Either[JobFailed, String]] = {
    tryLock {
      logger.info(s"[$jobName][invoke] - Job started")
      for {
        pendingRecords <- nrsSubmissionRecordsRepository.getPendingRecords
        sentRecords <- Future.sequence {
          logger.info(s"[invoke] - Retrieved ${pendingRecords.size} pending records to be sent to NRS")
          submitRecordsToNRS(pendingRecords)
        }
        recordsToDelete = sentRecords.filter(_.status == SENT)
        recordsToUpdate = sentRecords.filterNot(_.status == SENT)
        mongoDeleteResult <- if (recordsToDelete.nonEmpty) nrsSubmissionRecordsRepository.deleteRecords(recordsToDelete) else Future(Right(true))
        mongoWriteResult <- if (recordsToUpdate.nonEmpty) nrsSubmissionRecordsRepository.updateRecords(recordsToUpdate) else Future(Right(true))
        isSuccess = sentRecords.forall(_.status == SENT) && mongoWriteResult.isRight && mongoDeleteResult.isRight
      } yield {
        if (isSuccess) {
          logger.info("[invoke] - Processed all records in batch")
          Right("Processed all records")
        } else {
          PagerDutyHelper.log("invoke", FAILED_TO_PROCESS_RECORD)
          logger.error(s"[invoke] - Failed to process all records (see previous logs)")
          Left(FailedToProcessRecords)
        }
      }
    }
  }

  private def submitRecordsToNRS(records: Seq[NRSSubmissionRecord]): Seq[Future[NRSSubmissionRecord]] =
    records.map { record =>
      nrsConnector.submit(record.payload).map {
        _.fold({
          case Downstream4xxError =>
            logger.warn(s"[submitRecordsToNRS] - A 4xx error was returned from NRS for record: ${record.reference}, setting record to $RECORD_SET_TO_PERMANENTLY_FAILED")
            PagerDutyHelper.log("submitRecordsToNRS", RECORD_SET_TO_PERMANENTLY_FAILED)
            logger.debug(s"[submitRecordsToNRS] Bad request sent to NRS: ${record.payload}")
            record.copy(status = PERMANENTLY_FAILED)
          case _ =>
            logger.warn(s"[invoke] - Received error from NRS for record: ${record.reference}, setting to $FAILED_PENDING_RETRY")
            PagerDutyHelper.log("submitRecordsToNRS", RECORD_SET_TO_FAILED_PENDING_RETRY)
            logger.debug(s"[submitRecordsToNRS] Bad request sent to NRS: ${record.payload}")
            record.copy(status = FAILED_PENDING_RETRY)
        },
          _ => {
            logger.debug(s"[submitRecordsToNRS] - Success response received from NRS for record: ${record.reference}, setting to $SENT")
            record.copy(status = SENT)
          }
        )
      }
    }
}
