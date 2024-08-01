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

package mocks.config

import config.AppConfig
import org.scalamock.handlers.{CallHandler0, CallHandler1}
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite


trait MockAppConfig extends MockFactory { this: TestSuite =>
  lazy val mockAppConfig: AppConfig = mock[AppConfig]

  object MockedAppConfig {
    def getMongoLockTimeoutForJob(jobName: String): CallHandler1[String, Int] = {
      (mockAppConfig.getMongoLockTimeoutForJob(_: String)).expects(jobName)
    }

    def nrsSubmissionUrl: CallHandler0[String] = ((() => mockAppConfig.nrsSubmissionUrl): () => String).expects()
    def nonRepudiationServiceAPIKey: CallHandler0[String] = ((() => mockAppConfig.nonRepudiationServiceAPIKey): () => String).expects()
  }
}
