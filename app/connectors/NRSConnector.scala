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

import config.AppConfig
import models.request.NRSPayload
import models.response.{ErrorResponse, NRSSuccessResponse, UnexpectedDownstreamResponseError}
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import utils.Constants.{ERROR_MESSAGE_LOG_LIMIT, X_API_KEY}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NRSConnector @Inject()(val http: HttpClient,
                             config: AppConfig) extends NRSHttpParser[NRSSuccessResponse] {

  override implicit val reads: Reads[NRSSuccessResponse] = NRSSuccessResponse.format

  lazy val submissionUrl: String = config.nrsSubmissionUrl

  lazy val apiKey: String = config.nonRepudiationServiceAPIKey

  def submit(payload: NRSPayload)(implicit ec: ExecutionContext): Future[Either[ErrorResponse, NRSSuccessResponse]] = {
    val headerCarrierWithAPIKey = HeaderCarrier(extraHeaders = Seq(X_API_KEY -> apiKey))
    logger.debug(s"[submit] Sending following payload to NRS:\n\n ${Json.toJson(payload)}")
    post(submissionUrl, payload)(headerCarrierWithAPIKey, implicitly, implicitly).recover {
      case error =>
        logger.warn(s"[submit] Unexpected error from NRS: ${error.getClass} ${error.getMessage.take(ERROR_MESSAGE_LOG_LIMIT)}")
        Left(UnexpectedDownstreamResponseError)
    }
  }

}
