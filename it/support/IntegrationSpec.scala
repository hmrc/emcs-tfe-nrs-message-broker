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

package support

import connectors.WireMockHelper
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, EitherValues, OptionValues}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

trait IntegrationSpec extends AnyFreeSpec
  with WireMockHelper
  with GuiceOneServerPerSuite
  with ScalaFutures
  with Matchers
  with IntegrationPatience
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with EitherValues
  with OptionValues {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.nrs.port" -> WireMockHelper.wireMockPort,
      "microservice.services.nrs.apiKey" -> "TESTAPIKEY",
      "mongodb.numberOfRecordsToRetrieve" -> 5
    ).build()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    startWireMock()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    stopWireMock()
  }
}
