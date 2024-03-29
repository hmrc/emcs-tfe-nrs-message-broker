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

import fixtures.LockFixtures
import models.mongo.MongoLockResponses
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.LoggerLike
import support.{LogCapturing, UnitSpec}
import uk.gov.hmrc.mongo.lock.{LockRepository, LockService}
import utils.Logging
import utils.PagerDutyHelper.PagerDutyKeys

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}

class LockingSpec extends UnitSpec with LogCapturing with LockFixtures with Logging {

  val fakeJobName = "FakeJob"
  val mongoLockId: String = s"schedules.$fakeJobName"
  val mongoLockTimeout: Int = 123
  val releaseDuration: Duration = mongoLockTimeout.seconds

  lazy val fakeLockKeeper: LockService = new LockService {
    override val lockId: String = s"schedules.$fakeJobName"
    override val ttl: Duration = releaseDuration
    override val lockRepository: LockRepository = mockLockRepository
  }

  val fakeLogger: LoggerLike = logger

  object TestLocking extends Locking {
    override val lockKeeper: LockService = fakeLockKeeper
    override val jobName: String = fakeJobName
    override implicit val logger: LoggerLike = fakeLogger
  }

  class Setup {
    reset(mockLockRepository)
    when(mockLockRepository.takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
      .thenReturn(Future.successful(Some(testLock)))
    when(mockLockRepository.releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any()))
      .thenReturn(Future.successful(()))
  }

  "tryLock" - {
    "invoke the provided function when lockRepository is able to lock and unlock successfully" in new Setup {
      val expectingResult: Future[Right[Nothing, String]] = Future.successful(Right(""))

      when(mockLockRepository.takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.successful(Some(testLock)))
      when(mockLockRepository.releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any()))
        .thenReturn(Future.successful(()))

      TestLocking.tryLock(expectingResult).futureValue shouldBe Right("")

      verify(mockLockRepository, times(1)).takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any())
    }

    s"return a job already running if the lock fails to be created" in new Setup {
      val expectingResult: Future[Right[Nothing, String]] = Future.successful(Right(""))

      when(mockLockRepository.takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.successful(None))

      withCaptureOfLoggingFrom(logger) { capturedLogEvents =>

        TestLocking.tryLock(expectingResult).futureValue shouldBe Right(s"$fakeJobName - JobAlreadyRunning")

        capturedLogEvents.exists(_.getMessage == s"[LockingSpec][$fakeJobName] Locked because it might be running on another instance") shouldBe true
      }

      verify(mockLockRepository, times(1)).takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration))
      verify(mockLockRepository, times(0)).releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any())
    }

    s"return $Left ${MongoLockResponses.UnknownException} if lock returns exception, release lock is still called and succeeds" in new Setup {
      val exception = new Exception("woopsy")

      when(mockLockRepository.takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.failed(exception))
      when(mockLockRepository.releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any()))
        .thenReturn(Future.successful(()))

      withCaptureOfLoggingFrom(logger) { capturedLogEvents =>

        TestLocking.tryLock(Future.successful(Right(""))).futureValue shouldBe Left(MongoLockResponses.UnknownException(exception))

        capturedLogEvents.exists(_.getMessage == s"[LockingSpec][$fakeJobName] Failed with exception") shouldBe true
        capturedLogEvents.exists(_.getMessage.contains(PagerDutyKeys.MONGO_LOCK_UNKNOWN_EXCEPTION.toString)) shouldBe true
      }

      verify(mockLockRepository, times(1)).takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any())
    }

    s"return $Left ${MongoLockResponses.UnknownException} if lock returns exception, release lock is still called but failed" in new Setup {
      val exception = new Exception("not again")

      when(mockLockRepository.takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.failed(exception))
      when(mockLockRepository.releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any()))
        .thenReturn(Future.failed(exception))

      withCaptureOfLoggingFrom(logger) { capturedLogEvents =>

        TestLocking.tryLock(Future.successful(Right(""))).futureValue shouldBe Left(MongoLockResponses.UnknownException(exception))

        capturedLogEvents.exists(_.getMessage == s"[LockingSpec][$fakeJobName] Failed with exception") shouldBe true
        capturedLogEvents.exists(_.getMessage.contains(PagerDutyKeys.MONGO_LOCK_UNKNOWN_EXCEPTION.toString)) shouldBe true
      }

      verify(mockLockRepository, times(1)).takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any())
    }
  }
}
