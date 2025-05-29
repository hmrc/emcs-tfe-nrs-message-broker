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

package mocks.connectors

import fixtures.NRSFixtures
import izumi.reflect.Tag
import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.JsValue
import play.api.libs.ws.BodyWritable
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

trait MockHttpClient extends MockFactory with NRSFixtures { this: TestSuite =>

  val mockHttpClient: HttpClientV2 = mock[HttpClientV2]
  val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]

  object MockHttpClient extends Matchers {

    def post[T](url: URL,
                   body: JsValue): CallHandler[Future[T]] = {

      (mockHttpClient.post(_: URL)(_: HeaderCarrier))
        .expects(url, *)
        .returns(mockRequestBuilder)

      (mockRequestBuilder.withBody(_: JsValue)(_: BodyWritable[JsValue], _: Tag[JsValue], _: ExecutionContext))
        .expects(body, *, *, *)
        .returns(mockRequestBuilder)

      (mockRequestBuilder.execute[T](_: HttpReads[T], _: ExecutionContext))
        .expects(*, *)

    }

  }

}
