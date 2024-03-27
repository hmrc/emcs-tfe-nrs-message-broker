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

import models.mongo.MongoLockResponses
import play.api.LoggerLike
import scheduler.JobFailed
import uk.gov.hmrc.mongo.lock.LockService
import utils.PagerDutyHelper
import utils.PagerDutyHelper.PagerDutyKeys.MONGO_LOCK_UNKNOWN_EXCEPTION

import scala.concurrent.{ExecutionContext, Future}

trait Locking {

  val lockKeeper: LockService

  val jobName: String

  implicit val logger: LoggerLike

  def tryLock(f: => Future[Either[JobFailed, String]])(implicit ec: ExecutionContext): Future[Either[JobFailed, String]] = {
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
