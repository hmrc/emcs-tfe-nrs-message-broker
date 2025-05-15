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

package test.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import connectors.NRSConnector
import fixtures.NRSFixtures
import models.response.{Downstream4xxError, UnexpectedDownstreamResponseError}
import play.api.http.Status.{ACCEPTED, INTERNAL_SERVER_ERROR, NOT_FOUND}
import play.api.libs.json.Json
import test.support.IntegrationSpec

import scala.concurrent.ExecutionContext.Implicits.global

class NRSConnectorISpec extends IntegrationSpec with NRSFixtures {

  private lazy val connector: NRSConnector = app.injector.instanceOf[NRSConnector]

  val url = "/submission"

  ".submit" - {

    "should return the submission ID when the connector call was successful" in {

      wireMockServer.stubFor(
        post(urlEqualTo(url))
          .withHeader("X-API-Key", equalTo(testAPIKey))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(nrsPayload))))
          .willReturn(aResponse().withStatus(ACCEPTED).withBody(Json.stringify(Json.toJson(nrsSuccessResponseModel))))
      )

      connector.submit(nrsPayload).futureValue mustBe Right(nrsSuccessResponseModel)
    }

    "must return Downstream4xxError when the server responds NOT_FOUND" in {

      wireMockServer.stubFor(
        post(urlEqualTo(url))
          .withHeader("X-API-Key", equalTo(testAPIKey))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(nrsPayload))))
          .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      connector.submit(nrsPayload).futureValue mustBe Left(Downstream4xxError)
    }

    "must fail when the server responds with any other status" in {

      wireMockServer.stubFor(
        post(urlEqualTo(url))
          .withHeader("X-API-Key", equalTo(testAPIKey))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(nrsPayload))))
          .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      connector.submit(nrsPayload).futureValue mustBe Left(UnexpectedDownstreamResponseError)
    }

    "must fail when the connection fails" in {

      wireMockServer.stubFor(
        post(urlEqualTo(url))
          .withHeader("X-API-Key", equalTo(testAPIKey))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(nrsPayload))))
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
      )

      connector.submit(nrsPayload).futureValue mustBe Left(UnexpectedDownstreamResponseError)
    }
  }
}
