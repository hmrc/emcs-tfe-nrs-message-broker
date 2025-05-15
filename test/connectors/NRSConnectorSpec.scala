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

package connectors

import fixtures.NRSFixtures
import mocks.config.MockAppConfig
import mocks.connectors.MockHttpClient
import models.response.{Downstream4xxError, ErrorResponse, JsonValidationError, NRSSuccessResponse}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.libs.json.Json
import support.UnitSpec
import uk.gov.hmrc.http.StringContextOps

import scala.concurrent.Future

class NRSConnectorSpec extends UnitSpec
  with MockHttpClient
  with MockAppConfig
  with NRSFixtures
  with BeforeAndAfterEach {

  lazy val connector = new NRSConnector(mockHttpClient, mockAppConfig)

  val downstreamUrl = "http://localhost:0000/submission"

  class Setup(response: Either[ErrorResponse, NRSSuccessResponse]) {

    MockHttpClient.post(url"$downstreamUrl", Json.toJson(nrsPayload))
      .returns(Future.successful(response))
  }

  ".submit" - {

    MockedAppConfig.nrsSubmissionUrl.returns(downstreamUrl)

    MockedAppConfig.nonRepudiationServiceAPIKey.returns(testAPIKey)

    "should return a successful response" - {

      "when downstream call is successful and returns some JSON" in new Setup(Right(nrsSuccessResponseModel)) {

        connector.submit(nrsPayload).futureValue shouldBe Right(nrsSuccessResponseModel)
      }
    }

    "should return an error response" - {

      "when downstream call fails (due to a 4xx error)" in new Setup(Left(Downstream4xxError)) {

        connector.submit(nrsPayload).futureValue shouldBe Left(Downstream4xxError)
      }

      "when downstream call fails" in new Setup(Left(JsonValidationError)) {

        connector.submit(nrsPayload).futureValue shouldBe Left(JsonValidationError)
      }
    }
  }
}
