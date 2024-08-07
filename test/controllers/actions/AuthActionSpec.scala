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

import config.EnrolmentKeys
import fixtures.BaseFixtures
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import support.UnitSpec
import uk.gov.hmrc.auth.core.AffinityGroup.{Agent, Organisation}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AuthActionSpec extends UnitSpec with BaseFixtures {

  class FakeSuccessAuthConnector[B] @Inject()(response: B) extends AuthConnector {
    override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
      Future.successful(response.asInstanceOf[A])
  }

  class FakeFailingAuthConnector @Inject()(exceptionToReturn: Throwable) extends AuthConnector {
    override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
      Future.failed(exceptionToReturn)
  }

  lazy val bodyParsers = app.injector.instanceOf[BodyParsers.Default]
  implicit lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]

  type AuthRetrieval = ~[~[~[Option[AffinityGroup], Enrolments], Option[String]], Option[Credentials]]

  implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  trait Harness {

    val authConnector: AuthConnector
    lazy val authAction = new AuthActionImpl(authConnector, bodyParsers)
    def onPageLoad(): Action[AnyContent] = authAction(testErn) { _ => Results.Ok }

    lazy val result = onPageLoad()(fakeRequest)
  }

  def authResponse(affinityGroup: Option[AffinityGroup] = Some(Organisation),
                   enrolments: Enrolments = Enrolments(Set.empty),
                   internalId: Option[String] = Some(testInternalId),
                   credId: Option[Credentials] = Some(Credentials(testCredId, "gg"))): AuthRetrieval =
    new ~(new ~(new ~(affinityGroup, enrolments), internalId), credId)

  "AuthAction" - {

    "when calling .invokeBlock" - {

      "when the user is not logged in" - {

        "must return unauthorised" in new Harness {

          override val authConnector = new FakeFailingAuthConnector(new BearerTokenExpired)

          status(result) shouldBe UNAUTHORIZED
        }
      }

      "An unexpected Authorisation exception is returned from the Auth library" - {

        "must return unauthorised" in new Harness {

          override val authConnector = new FakeFailingAuthConnector(new InsufficientConfidenceLevel)

          status(result) shouldBe FORBIDDEN
        }
      }

      "User is logged in" - {

        "when Affinity Group of user does not exist" - {

          "must return unauthorised" in new Harness {

            override val authConnector = new FakeSuccessAuthConnector(authResponse(affinityGroup = None))

            status(result) shouldBe UNAUTHORIZED
          }
        }

        "when Affinity Group of user is not Organisation" - {

          "must return unauthorised" in new Harness {

            override val authConnector = new FakeSuccessAuthConnector(authResponse(affinityGroup = Some(Agent)))

            status(result) shouldBe UNAUTHORIZED
          }
        }

        "when Affinity Group of user is Organisation" - {

          "when internalId is not retrieved from Auth" - {

            "must return unauthorised" in new Harness {

              override val authConnector = new FakeSuccessAuthConnector(authResponse(internalId = None))

              status(result) shouldBe UNAUTHORIZED
            }
          }

          "when internalId is retrieved from Auth" - {

            "when credential is not retrieved from Auth" - {

              "must return unauthorised" in new Harness {

                override val authConnector = new FakeSuccessAuthConnector(authResponse(credId = None))

                status(result) shouldBe UNAUTHORIZED
              }
            }

            "when credential is retrieved from Auth" - {

              s"and Enrolments is missing the ${EnrolmentKeys.EMCS_ENROLMENT}" - {

                "must return unauthorised" in new Harness {

                  override val authConnector = new FakeSuccessAuthConnector(authResponse())

                  status(result) shouldBe FORBIDDEN
                }
              }

              s"when Enrolments exists for ${EnrolmentKeys.EMCS_ENROLMENT} but is NOT activated" - {

                "must return unauthorised" in new Harness {

                  override val authConnector = new FakeSuccessAuthConnector(authResponse(enrolments = Enrolments(Set(
                    Enrolment(
                      key = EnrolmentKeys.EMCS_ENROLMENT,
                      identifiers = Seq(EnrolmentIdentifier(EnrolmentKeys.ERN, testErn)),
                      state = EnrolmentKeys.INACTIVE
                    )
                  ))))

                  status(result) shouldBe FORBIDDEN
                }
              }

              s"when Enrolments exists for ${EnrolmentKeys.EMCS_ENROLMENT} AND is activated" - {

                s"and the ${EnrolmentKeys.ERN} identifier is missing (should be impossible)" - {

                  "return unauthorised" in new Harness {

                    override val authConnector = new FakeSuccessAuthConnector(authResponse(enrolments = Enrolments(Set(
                      Enrolment(
                        key = EnrolmentKeys.EMCS_ENROLMENT,
                        identifiers = Seq(),
                        state = EnrolmentKeys.ACTIVATED
                      )
                    ))))

                    status(result) shouldBe FORBIDDEN
                  }
                }

                s"when the ${EnrolmentKeys.ERN} identifier is present and matches ERN from URL" - {

                  "must allow the User through, returning a 200 (OK)" in new Harness {

                    override val authConnector = new FakeSuccessAuthConnector(authResponse(enrolments = Enrolments(Set(
                      Enrolment(
                        key = EnrolmentKeys.EMCS_ENROLMENT,
                        identifiers = Seq(EnrolmentIdentifier(EnrolmentKeys.ERN, testErn)),
                        state = EnrolmentKeys.ACTIVATED
                      )
                    ))))

                    status(result) shouldBe OK
                  }
                }

                s"when the ${EnrolmentKeys.ERN} identifier is present and DOES NOT match ERN from URL" - {

                  "must return Forbidden" in new Harness {

                    override val authConnector = new FakeSuccessAuthConnector(authResponse(enrolments = Enrolments(Set(
                      Enrolment(
                        key = EnrolmentKeys.EMCS_ENROLMENT,
                        identifiers = Seq(EnrolmentIdentifier(EnrolmentKeys.ERN, "other")),
                        state = EnrolmentKeys.ACTIVATED
                      )
                    ))))

                    status(result) shouldBe FORBIDDEN
                  }
                }

                s"when there are multiple Enrolments with ${EnrolmentKeys.ERN}'s present and ERN matches one" - {

                  "must allow the User through, returning a 200 (OK)" in new Harness {

                    override val authConnector = new FakeSuccessAuthConnector(authResponse(enrolments = Enrolments(Set(
                      Enrolment(
                        key = EnrolmentKeys.EMCS_ENROLMENT,
                        identifiers = Seq(EnrolmentIdentifier(EnrolmentKeys.ERN, "OTHER_1")),
                        state = EnrolmentKeys.INACTIVE
                      ),
                      Enrolment(
                        key = EnrolmentKeys.EMCS_ENROLMENT,
                        identifiers = Seq(EnrolmentIdentifier(EnrolmentKeys.ERN, testErn)),
                        state = EnrolmentKeys.ACTIVATED
                      ),
                      Enrolment(
                        key = EnrolmentKeys.EMCS_ENROLMENT,
                        identifiers = Seq(EnrolmentIdentifier(EnrolmentKeys.ERN, "OTHER_2")),
                        state = EnrolmentKeys.ACTIVATED
                      )
                    ))))

                    status(result) shouldBe OK
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

