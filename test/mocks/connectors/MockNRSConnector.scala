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

import connectors.NRSConnector
import models.request.NRSPayload
import models.response.{ErrorResponse, NRSSuccessResponse}
import org.scalamock.handlers.CallHandler2
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite

import scala.concurrent.{ExecutionContext, Future}

trait MockNRSConnector extends MockFactory { this: TestSuite =>

  lazy val mockNRSConnector: NRSConnector = mock[NRSConnector]

  object MockedNRSConnector {

    def submit(payload: NRSPayload): CallHandler2[NRSPayload, ExecutionContext, Future[Either[ErrorResponse, NRSSuccessResponse]]] =
      (mockNRSConnector.submit(_: NRSPayload)(_: ExecutionContext))
        .expects(payload, *)

  }

}
