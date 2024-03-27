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

import controllers.actions.{AuthAction, AuthActionHelper}
import models.mongo.NRSSubmissionRecord
import models.request.NRSPayload
import play.api.libs.json.JsValue
import play.api.mvc.{Action, ControllerComponents}
import repositories.NRSSubmissionRecordsRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.{Logging, TimeMachine, UUIDGenerator}

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class NRSPayloadController @Inject()(cc: ControllerComponents,
                                     nrsSubmissionRecordsRepository: NRSSubmissionRecordsRepository,
                                     timeMachine: TimeMachine,
                                     uuidGenerator: UUIDGenerator,
                                     override val auth: AuthAction
                                    )(implicit ec: ExecutionContext) extends BackendController(cc) with Logging with AuthActionHelper {

  def insertRecord(ern: String): Action[JsValue] = authorisedUserSubmissionRequest(ern) { implicit request =>
    withJsonBody[NRSPayload] {
      nrsPayload =>
        val submissionRecord = NRSSubmissionRecord(payload = nrsPayload, updatedAt = timeMachine.now, reference = uuidGenerator.uuidAsString)
        nrsSubmissionRecordsRepository.insertRecord(submissionRecord)
          .map(_ => Accepted(""))
          .recover {
            case e =>
              logger.warn(s"[insertRecord] Failed to insert NRS payload with error: ${e.getMessage.take(10000)}")
              InternalServerError("Failed to insert NRS payload")
          }
    }
  }

}