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

import com.mongodb.bulk.BulkWriteResult
import fixtures.{LockFixtures, NRSFixtures}
import mocks.config.MockAppConfig
import mocks.connectors.MockNRSConnector
import mocks.repositories.MockNRSSubmissionRecordsRepository
import models.FailedJobResponses.FailedToProcessRecords
import models.mongo.MongoOperationResponses.BulkWriteFailure
import models.mongo.NRSSubmissionRecord
import models.mongo.RecordStatusEnum.{FAILED_PENDING_RETRY, PENDING, SENT}
import models.response.UnexpectedDownstreamResponseError
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{reset, when}
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import scheduler.JobFailed
import support.UnitSpec

import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}

class SendSubmissionToNRSServiceSpec extends UnitSpec
  with MockAppConfig
  with LockFixtures
  with MockNRSSubmissionRecordsRepository
  with MockNRSConnector
  with NRSFixtures {

  val service = new SendSubmissionToNRSService(mockLockRepository, mockNRSSubmissionRecordsRepository, mockNRSConnector, mockAppConfig)

  val jobName = "SendSubmissionToNRSJob"
  val mongoLockId: String = s"schedules.$jobName"
  val mongoLockTimeout: Int = 123
  val releaseDuration: Duration = mongoLockTimeout.seconds
  val instantNow: Instant = Instant.now()

  val records: Seq[NRSSubmissionRecord] = Seq(
    NRSSubmissionRecord(nrsPayload, status = PENDING, reference = "ref1", updatedAt = instantNow),
    NRSSubmissionRecord(nrsPayload, status = PENDING, reference = "ref2", updatedAt = instantNow)
  )

  class Setup {
    reset(mockLockRepository)
    when(mockLockRepository.takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
      .thenReturn(Future.successful(Some(testLock)))
    when(mockLockRepository.releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any()))
      .thenReturn(Future.successful(()))
  }

  "invoke" - {
    "return 'Processed all records'" - {

      MockedAppConfig.getMongoLockTimeoutForJob(jobName).returns(mongoLockTimeout)

      "when no records are retrieved from Mongo" in new Setup {

        MockedNRSSubmissionRecordsRepository.getPendingRecords.returns(Future.successful(Seq.empty))

        val result: Either[JobFailed, String] = service.invoke.futureValue
        result shouldBe Right("Processed all records")
      }

      "when pending records are retrieved from Mongo, all are successfully sent to NRS and are successfully updated in Mongo" in new Setup {

        MockedNRSSubmissionRecordsRepository.getPendingRecords.returns(Future.successful(records))

        MockedNRSConnector.submit(records.head.payload).returns(Future.successful(Right(nrsSuccessResponseModel)))
        MockedNRSConnector.submit(records(1).payload).returns(Future.successful(Right(nrsSuccessResponseModel)))

        MockedNRSSubmissionRecordsRepository.updateRecords(records.map(_.copy(status = SENT))).returns(Future.successful(Right(true)))

        val result: Either[JobFailed, String] = service.invoke.futureValue
        result shouldBe Right("Processed all records")
      }
    }

    "return 'FailedToProcessRecords'" - {

      "when all records sent to NRS fail" in new Setup {

        MockedNRSSubmissionRecordsRepository.getPendingRecords.returns(Future.successful(records))

        MockedNRSConnector.submit(records.head.payload).returns(Future.successful(Left(UnexpectedDownstreamResponseError)))
        MockedNRSConnector.submit(records(1).payload).returns(Future.successful(Left(UnexpectedDownstreamResponseError)))

        MockedNRSSubmissionRecordsRepository.updateRecords(records.map(_.copy(status = FAILED_PENDING_RETRY))).returns(Future.successful(Right(true)))

        val result: Either[JobFailed, String] = service.invoke.futureValue
        result shouldBe Left(FailedToProcessRecords)
      }

      "when some records sent to NRS fail" in new Setup {

        MockedNRSSubmissionRecordsRepository.getPendingRecords.returns(Future.successful(records))

        MockedNRSConnector.submit(records.head.payload).returns(Future.successful(Right(nrsSuccessResponseModel)))
        MockedNRSConnector.submit(records(1).payload).returns(Future.successful(Left(UnexpectedDownstreamResponseError)))

        MockedNRSSubmissionRecordsRepository.updateRecords(Seq(records.head.copy(status = SENT), records(1).copy(status = FAILED_PENDING_RETRY))).returns(Future.successful(Right(true)))

        val result: Either[JobFailed, String] = service.invoke.futureValue
        result shouldBe Left(FailedToProcessRecords)
      }

      "when all records are sent to NRS but fail to get updated in Mongo" in new Setup {

        MockedNRSSubmissionRecordsRepository.getPendingRecords.returns(Future.successful(records))

        MockedNRSConnector.submit(records.head.payload).returns(Future.successful(Right(nrsSuccessResponseModel)))
        MockedNRSConnector.submit(records(1).payload).returns(Future.successful(Right(nrsSuccessResponseModel)))

        MockedNRSSubmissionRecordsRepository.updateRecords(records.map(_.copy(status = SENT))).returns(Future.successful(Left(BulkWriteFailure(BulkWriteResult.unacknowledged()))))

        val result: Either[JobFailed, String] = service.invoke.futureValue
        result shouldBe Left(FailedToProcessRecords)
      }
    }
  }
}
