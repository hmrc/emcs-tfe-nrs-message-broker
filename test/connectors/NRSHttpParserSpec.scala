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

import mocks.connector.MockHttpClient
import models.response.{JsonValidationError, NRSSuccessResponse, UnexpectedDownstreamResponseError}
import play.api.http.Status
import play.api.libs.json.{Json, Reads}
import support.UnitSpec
import uk.gov.hmrc.http.{HttpClient, HttpResponse}

class NRSHttpParserSpec extends UnitSpec with MockHttpClient{

  lazy val httpParser: NRSHttpParser[NRSSuccessResponse] = new NRSHttpParser[NRSSuccessResponse] {
    override implicit val reads: Reads[NRSSuccessResponse] = NRSSuccessResponse.format
    override def http: HttpClient = mockHttpClient
  }

  "NRSReads.read(method: String, url: String, response: HttpResponse)" - {

    "should return a successful response" - {

      "when valid JSON is returned that can be parsed to the model" in {

        val httpResponse = HttpResponse(Status.ACCEPTED, nrsSuccessResponseJson, Map())

        httpParser.NRSReads.read("POST", "/submission", httpResponse) shouldBe Right(nrsSuccessResponseModel)
      }
    }

    "should return UnexpectedDownstreamError" - {

      s"when status is not ACCEPTED (${Status.ACCEPTED})" in {

        val httpResponse = HttpResponse(Status.INTERNAL_SERVER_ERROR, Json.obj(), Map())

        httpParser.NRSReads.read("POST", "/submission", httpResponse) shouldBe Left(UnexpectedDownstreamResponseError)
      }
    }

    "should return JsonValidationError" - {

      s"when response does not contain Json" in {

        val httpResponse = HttpResponse(Status.ACCEPTED, "", Map())

        httpParser.NRSReads.read("POST", "/submission", httpResponse) shouldBe Left(JsonValidationError)
      }

      s"when response contains JSON but can't be deserialized to model" in {

        val httpResponse = HttpResponse(Status.ACCEPTED, Json.obj(), Map())

        httpParser.NRSReads.read("POST", "/submission", httpResponse) shouldBe Left(JsonValidationError)
      }
    }
  }
}
