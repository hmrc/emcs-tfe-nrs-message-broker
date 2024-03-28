package repositories

import com.mongodb.bulk.BulkWriteResult
import fixtures.NRSFixtures
import mocks.utils.generators.FakeTimeMachine
import models.mongo.MongoOperationResponses.BulkWriteFailure
import models.mongo.NRSSubmissionRecord
import models.mongo.RecordStatusEnum.{FAILED_PENDING_RETRY, PENDING, PERMANENTLY_FAILED, SENT}
import org.mongodb.scala.Document
import play.api.test.Helpers._

import java.util.Collections.emptyList

class NRSSubmissionRecordsRepositoryISpec extends RepositoryBaseSpec[NRSSubmissionRecord] with NRSFixtures with FakeTimeMachine {

  override protected val repository: NRSSubmissionRecordsRepositoryImpl = new NRSSubmissionRecordsRepositoryImpl(
    mongoComponent = mongoComponent, appConfig = appConfig, timeMachine = mockTimeMachine
  )

  override protected def afterEach(): Unit = {
    super.afterEach()
    repository.collection.deleteMany(Document()).toFuture().futureValue
  }

  "getPendingRecords" - {

    s"should return all records in the $PENDING - if only $PENDING records exist" in {

      val records = Seq(
        NRSSubmissionRecord(nrsPayload, status = PENDING, reference = "ref1", updatedAt = instantNow),
        NRSSubmissionRecord(nrsPayload, status = PENDING, reference = "ref2", updatedAt = instantNow)
      )

      await(repository.collection.insertMany(records).toFuture())

      val result = await(repository.getPendingRecords)

      result mustBe records
    }

    s"should return all records in the $FAILED_PENDING_RETRY state - if only $FAILED_PENDING_RETRY records exist" in {

      val records = Seq(
        NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref1", updatedAt = instantNow),
        NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref2", updatedAt = instantNow)
      )

      await(repository.collection.insertMany(records).toFuture())

      val result = await(repository.getPendingRecords)

      result mustBe records
    }

    s"should return all records in the $PENDING and $FAILED_PENDING_RETRY status" in {

      val records = Seq(
        NRSSubmissionRecord(nrsPayload, status = PENDING, reference = "ref1", updatedAt = instantNow),
        NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref2", updatedAt = instantNow),
        NRSSubmissionRecord(nrsPayload, status = SENT, reference = "ref3", updatedAt = instantNow),
        NRSSubmissionRecord(nrsPayload, status = PERMANENTLY_FAILED, reference = "ref4", updatedAt = instantNow)
      )

      await(repository.collection.insertMany(records).toFuture())

      val result = await(repository.getPendingRecords)

      result mustBe Seq(
        NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref2", updatedAt = instantNow),
        NRSSubmissionRecord(nrsPayload, status = PENDING, reference = "ref1", updatedAt = instantNow)
      )
    }

    s"should return only the specified number of records in the $PENDING and $FAILED_PENDING_RETRY status" in {

      val records = Seq(
        NRSSubmissionRecord(nrsPayload, status = PENDING, reference = "ref1", updatedAt = instantNow),
        NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref2", updatedAt = instantNow),
        NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref3", updatedAt = instantNow),
        NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref4", updatedAt = instantNow),
        NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref5", updatedAt = instantNow),
        NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref6", updatedAt = instantNow),
        NRSSubmissionRecord(nrsPayload, status = SENT, reference = "ref7", updatedAt = instantNow)
      )

      await(repository.collection.insertMany(records).toFuture())

      val result = await(repository.getPendingRecords)

      result.size mustBe 5
    }
  }

  "insertRecord" - {

    "should return true" - {

      "when the record is successfully inserted" in {

        val records = NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref2", updatedAt = instantNow)

        await(repository.insertRecord(records)) mustBe true
      }
    }
  }

  "updateRecords" - {

    "should return Left(BulkWriteFailure)" - {

      "when there is a difference between the records requested to be updated and the actual amount updated" in {

        val records = Seq(
          NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref2", updatedAt = instantNow),
          NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref2", updatedAt = instantNow)
        )

        //insertedCount, matchedCount, removedCount, modifiedCount, upserts, inserts
        val writeResult = BulkWriteFailure(BulkWriteResult.acknowledged(0, 2, 0, 1, emptyList(), emptyList()))

        await(repository.collection.insertMany(records.take(1)).toFuture())

        await(repository.updateRecords(records.map(_.copy(status = SENT)))) mustBe Left(writeResult)
        await(repository.collection.find(Document()).toFuture()) mustBe Seq(records.head.copy(status = SENT))
      }
    }

    "should return Right(true)" - {

      "all records are updated successfully" in {

        val records = Seq(
          NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref2", updatedAt = instantNow),
          NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref4", updatedAt = instantNow)
        )

        await(repository.collection.insertMany(records).toFuture())

        await(repository.updateRecords(records.map(_.copy(status = SENT)))) mustBe Right(true)
      }

    }
  }

  "updateRecords" - {

    "should return Right(true)" - {

      "all records are deleted successfully" in {

        val records = Seq(
          NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref2", updatedAt = instantNow),
          NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref4", updatedAt = instantNow)
        )

        await(repository.collection.insertMany(records).toFuture())

        await(repository.deleteRecords(records.map(_.copy(status = SENT)))) mustBe Right(true)

        await(repository.collection.countDocuments().toFuture()) mustBe 0
      }

    }
  }

  "countRecordsByStatus" - {

    "count all the records in the specified status" in {

      val records = Seq(
        NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref2", updatedAt = instantNow),
        NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref4", updatedAt = instantNow)
      )

      await(repository.collection.insertMany(records).toFuture())

      await(repository.countRecordsByStatus(FAILED_PENDING_RETRY)) mustBe 2
    }

    "return 0 if there are no records in the specified status" in {

      val records = Seq(
        NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref2", updatedAt = instantNow),
        NRSSubmissionRecord(nrsPayload, status = FAILED_PENDING_RETRY, reference = "ref4", updatedAt = instantNow)
      )

      await(repository.collection.insertMany(records).toFuture())

      await(repository.countRecordsByStatus(SENT)) mustBe 0
    }
  }
}
