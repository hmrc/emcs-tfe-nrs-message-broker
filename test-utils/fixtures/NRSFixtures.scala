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

package fixtures

import models.request.{NRSMetadata, NRSPayload}
import models.response.NRSSuccessResponse
import play.api.libs.json.{JsObject, Json}

import java.time.Instant

trait NRSFixtures {

  val testAPIKey: String = "TESTAPIKEY"

  val nrsPayload: NRSPayload = NRSPayload(
    "encodedPayload",
    metadata = NRSMetadata(
      businessId = "emcs",
      notableEvent = "emcs-create-a-movement-ui",
      payloadContentType = "application/json",
      payloadSha256Checksum = "sha256Checksum",
      userSubmissionTimestamp = Instant.parse("2024-03-01T12:32:22.00Z"),
      identityData = Json.obj(
        "identityKey" -> "identityValue"
      ),
      userAuthToken = "auth123",
      headerData = Json.obj(
        "headerKey" -> "headerValue"
      ),
      searchKeys = Json.obj(
        "key" -> "value"
      )
    )
  )

  val nrsSuccessResponseModel: NRSSuccessResponse = NRSSuccessResponse(nrSubmissionId = "submission1")

  val nrsSuccessResponseJson: JsObject = Json.obj(
    "nrSubmissionId" -> "submission1"
  )
}
