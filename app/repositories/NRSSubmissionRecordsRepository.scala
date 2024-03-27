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

package repositories

import com.google.inject.ImplementedBy
import config.AppConfig
import models.mongo.MongoOperationResponses.BulkWriteFailure
import models.mongo.{NRSSubmissionRecord, RecordStatusEnum}
import org.mongodb.scala.BulkWriteResult
import org.mongodb.scala.model.Filters.in
import org.mongodb.scala.model._
import play.api.libs.json.Format
import repositories.NRSSubmissionRecordsRepository._
import scheduler.JobFailed
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import utils.{Logging, TimeMachine}

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[NRSSubmissionRecordsRepositoryImpl])
trait NRSSubmissionRecordsRepository {
  def getPendingRecords: Future[Seq[NRSSubmissionRecord]]

  def insertRecord(record: NRSSubmissionRecord): Future[Boolean]

  def updateRecords(records: Seq[NRSSubmissionRecord]): Future[Either[JobFailed, Boolean]]
}

@Singleton
class NRSSubmissionRecordsRepositoryImpl @Inject()(mongoComponent: MongoComponent,
                                               appConfig: AppConfig,
                                               timeMachine: TimeMachine
                                              )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[NRSSubmissionRecord](
    collectionName = "nrs-submission-records",
    mongoComponent = mongoComponent,
    domainFormat = NRSSubmissionRecord.format,
    indexes = mongoIndexes(appConfig.mongoTTL),
    replaceIndexes = appConfig.mongoReplaceIndexes
  ) with Logging with NRSSubmissionRecordsRepository {

  implicit val instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

  def getPendingRecords: Future[Seq[NRSSubmissionRecord]] =
    collection.find(in(statusField, Seq(
      RecordStatusEnum.PENDING.toString,
      RecordStatusEnum.FAILED_PENDING_RETRY.toString): _*
    )).limit(appConfig.numberOfRecordsToRetrieve).toFuture()

  def insertRecord(record: NRSSubmissionRecord): Future[Boolean] =
    collection
      .insertOne(record)
      .toFuture()
      .map(_ => true)

  def updateRecords(records: Seq[NRSSubmissionRecord]): Future[Either[JobFailed, Boolean]] = {

    logger.info(s"[updateRecords] - Updating ${records.size} records in Mongo")

    val updatedRecordsWriteModel = records.map { record =>
      new UpdateOneModel(
        Filters.equal(referenceField, record.reference),
        Updates.combine(
          Updates.set(statusField, record.status.toString),
          Updates.set(updatedAtField, Codecs.toBson(timeMachine.now))
        )
      )
    }

    collection
      .bulkWrite(
        updatedRecordsWriteModel,
        BulkWriteOptions().ordered(false)
      )
      .toFuture()
      .flatMap(bulkWriteResultHandlerForUpdates(records, _))
  }

  private def bulkWriteResultHandlerForUpdates(originalRecords: Seq[NRSSubmissionRecord], bulkWriteResult: BulkWriteResult)
  : Future[Either[JobFailed, Boolean]] = {
    (originalRecords, bulkWriteResult) match {

      case (seqOfRecordsToReturn, bulkWriteResult) if bulkWriteResult.getModifiedCount == seqOfRecordsToReturn.size => Future.successful(Right(true))

      case (seqOfRecordsToReturn, bulkWriteResult) =>
        logger.warn(s"[bulkWriteResultHandlerForUpdates] ${seqOfRecordsToReturn.size} records requested to be updated - ${bulkWriteResult.getModifiedCount} actually updated.")
        Future.successful(Left(BulkWriteFailure(bulkWriteResult)))
    }
  }
}

object NRSSubmissionRecordsRepository {

  val updatedAtField = "updatedAt"

  val referenceField = "reference"

  val statusField = "status"

  def mongoIndexes(timeToLive: Duration): Seq[IndexModel] = Seq(
    IndexModel(
      Indexes.ascending(updatedAtField),
      IndexOptions()
        .name("updatedAtIdx")
        .expireAfter(timeToLive.toSeconds, TimeUnit.SECONDS)
    ),
    IndexModel(
      Indexes.ascending(referenceField),
      IndexOptions()
        .name("referenceIdx")
        .unique(true)
    ),
    IndexModel(
      Indexes.ascending(statusField),
      IndexOptions()
        .name("statusIdx")
    )
  )
}