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

import fixtures.{LockFixtures, NRSFixtures}
import mocks.config.MockAppConfig
import mocks.repositories.MockNRSSubmissionRecordsRepository
import models.mongo.RecordStatusEnum.{FAILED_PENDING_RETRY, PENDING, SENT}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{reset, when}
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import scheduler.JobFailed
import support.{LogCapturing, UnitSpec}

import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}

class MonitoringJobServiceSpec extends UnitSpec
  with MockAppConfig
  with LogCapturing
  with LockFixtures
  with MockNRSSubmissionRecordsRepository
  with NRSFixtures {

  val service = new MonitoringJobService(mockLockRepository, mockNRSSubmissionRecordsRepository, mockAppConfig)

  val jobName = "MonitoringJob"
  val mongoLockId: String = s"schedules.$jobName"
  val mongoLockTimeout: Int = 123
  val releaseDuration: Duration = mongoLockTimeout.seconds
  val instantNow: Instant = Instant.now()

  class Setup {
    reset(mockLockRepository)
    when(mockLockRepository.takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
      .thenReturn(Future.successful(Some(testLock)))
    when(mockLockRepository.releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any()))
      .thenReturn(Future.successful(()))
  }

  "invoke" - {

    "return 'Successfully ran monitoring job'" - {

      "when records are counted successfully in Mongo" in new Setup {

        MockedAppConfig.getMongoLockTimeoutForJob(jobName).returns(mongoLockTimeout)

        MockedNRSSubmissionRecordsRepository.countRecordsByStatus(PENDING).returns(Future.successful(1))
        MockedNRSSubmissionRecordsRepository.countRecordsByStatus(SENT).returns(Future.successful(2))
        MockedNRSSubmissionRecordsRepository.countRecordsByStatus(FAILED_PENDING_RETRY).returns(Future.successful(3))

        withCaptureOfLoggingFrom(service.logger) { logs =>
          val result: Either[JobFailed, String] = service.invoke.futureValue
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of Pending records: 1") shouldBe true
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of Sent records: 2") shouldBe true
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of Failed Pending Retry records: 3") shouldBe true
          result shouldBe Right("Successfully ran monitoring job")
        }

      }
    }
  }
}
