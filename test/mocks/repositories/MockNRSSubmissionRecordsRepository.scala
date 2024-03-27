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

package mocks.repositories

import models.mongo.{NRSSubmissionRecord, RecordStatusEnum}
import org.scalamock.handlers.{CallHandler0, CallHandler1}
import org.scalamock.scalatest.MockFactory
import repositories.NRSSubmissionRecordsRepository
import scheduler.JobFailed

import scala.concurrent.Future

trait MockNRSSubmissionRecordsRepository extends MockFactory {

  lazy val mockNRSSubmissionRecordsRepository: NRSSubmissionRecordsRepository = mock[NRSSubmissionRecordsRepository]

  object MockedNRSSubmissionRecordsRepository {
    def getPendingRecords: CallHandler0[Future[Seq[NRSSubmissionRecord]]] = ((() => mockNRSSubmissionRecordsRepository.getPendingRecords): () => Future[Seq[NRSSubmissionRecord]]).expects()

    def insertRecords(record: NRSSubmissionRecord): CallHandler1[NRSSubmissionRecord, Future[Boolean]] =
      (mockNRSSubmissionRecordsRepository.insertRecord(_: NRSSubmissionRecord))
        .expects(record)

    def updateRecords(updatedRecords: Seq[NRSSubmissionRecord]): CallHandler1[Seq[NRSSubmissionRecord], Future[Either[JobFailed, Boolean]]] =
      (mockNRSSubmissionRecordsRepository.updateRecords(_: Seq[NRSSubmissionRecord]))
        .expects(updatedRecords)

    def countRecordsByStatus(status: RecordStatusEnum.Value): CallHandler1[RecordStatusEnum.Value, Future[Long]] = (mockNRSSubmissionRecordsRepository.countRecordsByStatus(_: RecordStatusEnum.Value)).expects(status)
  }

}
