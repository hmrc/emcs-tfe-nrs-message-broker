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

package controllers.actions

import fixtures.BaseFixtures
import models.auth.UserRequest
import play.api.mvc.Results.Forbidden
import play.api.mvc._
import play.api.test.StubBodyParserFactory
import support.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

trait FakeAuthAction extends StubBodyParserFactory with BaseFixtures { _: UnitSpec =>

  object FakeSuccessAuthAction extends AuthAction {
    override def apply(ern: String): ActionBuilder[UserRequest, AnyContent] with ActionFunction[Request, UserRequest] =
      new ActionBuilder[UserRequest, AnyContent] with ActionFunction[Request, UserRequest] {

        override implicit protected def executionContext: ExecutionContext = ec

        override def parser: BodyParser[AnyContent] = stubBodyParser()

        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(UserRequest(request, testErn, testInternalId, testCredId))
      }
  }

  object FakeFailedAuthAction extends AuthAction {
    override def apply(ern: String): ActionBuilder[UserRequest, AnyContent] with ActionFunction[Request, UserRequest] =
      new ActionBuilder[UserRequest, AnyContent] with ActionFunction[Request, UserRequest] {

        override implicit protected def executionContext: ExecutionContext = ec

        override def parser: BodyParser[AnyContent] = stubBodyParser()

        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          Future.successful(Forbidden)
      }
  }
}
