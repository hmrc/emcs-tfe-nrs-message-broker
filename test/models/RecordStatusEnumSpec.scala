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

package models

import models.mongo.RecordStatusEnum
import play.api.libs.json.{JsString, Json}
import support.UnitSpec

class RecordStatusEnumSpec extends UnitSpec {

  def writableTest(expectedResult: String, statusEnum: RecordStatusEnum.Value): Unit = {
    s"status is ${statusEnum.toString}" in {
      val result = Json.toJson(statusEnum)
      result shouldBe JsString(expectedResult)
    }
  }

  def readableTest(expectedResult: RecordStatusEnum.Value, statusAsString: String): Unit = {
    s"status is $statusAsString" in {
      val result = Json.fromJson(JsString(statusAsString))(RecordStatusEnum.format)
      result.isSuccess shouldBe true
      result.get shouldBe expectedResult
    }
  }

  "RecordStatusEnum" - {

    "should be writable to Json" - {
      writableTest("PENDING", RecordStatusEnum.PENDING)
      writableTest("SENT", RecordStatusEnum.SENT)
      writableTest("FAILED_PENDING_RETRY", RecordStatusEnum.FAILED_PENDING_RETRY)
    }

    "should be readable from Json" - {
      readableTest(RecordStatusEnum.PENDING, "PENDING")
      readableTest(RecordStatusEnum.SENT, "SENT")
      readableTest(RecordStatusEnum.FAILED_PENDING_RETRY, "FAILED_PENDING_RETRY")
    }

    "throw an error" - {
      "when the value can't be read" in {
        val result = Json.fromJson(JsString("xyz"))(RecordStatusEnum.format)
        result.isError shouldBe true
      }
    }
  }
}
