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

import models.response.{Downstream4xxError, ErrorResponse, JsonValidationError, UnexpectedDownstreamResponseError}
import play.api.http.Status.ACCEPTED
import play.api.libs.json.Writes
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

trait NRSHttpParser[A] extends BaseConnectorUtils[A] {
    def http: HttpClient

    implicit object NRSReads extends HttpReads[Either[ErrorResponse, A]] {
      override def read(method: String, url: String, response: HttpResponse): Either[ErrorResponse, A] = {
        response.status match {
          case ACCEPTED => response.validateJson match {
            case Some(valid) => Right(valid)
            case None =>
              logger.warn(s"[read] Bad JSON response from NRS")
              Left(JsonValidationError)
          }
          case status if status >= 400 && status <= 499 =>
            logger.warn(s"[read] Received 4xx status NRS. Response status: $status")
            Left(Downstream4xxError)
          case status =>
            logger.warn(s"[read] Unexpected status from NRS: $status")
            Left(UnexpectedDownstreamResponseError)
        }
      }
    }

    def post[I](url: String, body: I)(implicit hc: HeaderCarrier, ec: ExecutionContext, writes: Writes[I]): Future[Either[ErrorResponse, A]] =
      http.POST[I, Either[ErrorResponse, A]](url, body)(writes, NRSReads, hc, ec)
  }

