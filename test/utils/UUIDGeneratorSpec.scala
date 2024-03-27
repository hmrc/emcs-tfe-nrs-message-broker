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

package utils

import support.UnitSpec

class UUIDGeneratorSpec extends UnitSpec {

  val uuidGenerator = new UUIDGenerator

  "UUIDGenerator" - {
    "should generate ids that are uuids" in {
      uuidGenerator.uuidAsString should fullyMatch regex """([a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8})""".r
    }
  }
}
