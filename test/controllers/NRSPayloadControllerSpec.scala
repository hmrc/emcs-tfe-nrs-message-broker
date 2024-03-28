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

package controllers

import controllers.actions.{AuthAction, FakeAuthAction}
import fixtures.{BaseFixtures, NRSFixtures}
import mocks.repositories.MockNRSSubmissionRecordsRepository
import mocks.utils.{FakeTimeMachine, FakeUUIDGenerator}
import models.mongo.NRSSubmissionRecord
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import support.UnitSpec

import scala.concurrent.Future

class NRSPayloadControllerSpec extends UnitSpec
  with FakeTimeMachine
  with FakeUUIDGenerator
  with BaseFixtures
  with NRSFixtures
  with MockNRSSubmissionRecordsRepository
  with FakeAuthAction {

  val nrsRecord: NRSSubmissionRecord = NRSSubmissionRecord(payload = nrsPayload, updatedAt = instantNow, reference = uuid)

  class Fixture(authAction: AuthAction) {
    val fakeRequest = FakeRequest("POST", s"/trader/$testErn/nrs/submission").withBody(Json.toJson(nrsPayload))

    val controller  = new NRSPayloadController(Helpers.stubControllerComponents(), mockNRSSubmissionRecordsRepository, mockTimeMachine, mockUUIDGenerator, authAction)
  }

  s"PUT ${routes.NRSPayloadController.insertRecord(testErn)}" - {

    "when the user is authorised" - {
      s"must return ${Status.ACCEPTED} (ACCEPTED)" - {
        "when the repository call is successful" in new Fixture(FakeSuccessAuthAction) {

          MockedNRSSubmissionRecordsRepository.insertRecord(nrsRecord).returns(Future.successful(true))

          val result = controller.insertRecord(testErn)(fakeRequest)

          status(result) shouldBe Status.ACCEPTED
          contentAsJson(result) shouldBe Json.obj("reference" -> nrsRecord.reference)
        }
      }

      s"return ${Status.INTERNAL_SERVER_ERROR} (ISE)" - {
        "when the repository call fails" in new Fixture(FakeSuccessAuthAction) {

          MockedNRSSubmissionRecordsRepository.insertRecord(nrsRecord).returns(Future.failed(new Exception("Bazinga")))

          val result = controller.insertRecord(testErn)(fakeRequest)

          status(result) shouldBe Status.INTERNAL_SERVER_ERROR
          contentAsString(result) shouldBe "Failed to insert NRS payload"
        }
      }
    }

    "user is NOT authorised" - {
      s"must return ${Status.FORBIDDEN} (FORBIDDEN)" in new Fixture(FakeFailedAuthAction) {

        val result = controller.insertRecord(testErn)(fakeRequest)

        status(result) shouldBe Status.FORBIDDEN
      }
    }

  }
}
