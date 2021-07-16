package com.evernym.verity.integration.with_basic_sdk.oauth

import com.evernym.verity.agentmsg.msgfamily.configs.{ComMethod, ComMethodAuthentication, UpdateComMethodReqMsg}
import com.evernym.verity.constants.Constants.COM_METHOD_TYPE_HTTP_ENDPOINT
import com.evernym.verity.integration.base.sdk_provider.{OAuthParam, SdkProvider}
import com.evernym.verity.integration.base.{CAS, VAS, VerityProviderBaseSpec}
import com.evernym.verity.protocol.protocols.relationship.v_1_0.Signal.Invitation

import scala.concurrent.duration._


class ComMethodAuthenticationSpec
  extends VerityProviderBaseSpec
    with SdkProvider {

  lazy val issuerVerityEnv = VerityEnvBuilder.default().build(VAS)
  lazy val holderVerityEnv = VerityEnvBuilder.default().build(CAS)

  lazy val issuerSDK = setupIssuerSdk(issuerVerityEnv, Option(OAuthParam(5.seconds)))
  lazy val holderSDK = setupHolderSdk(holderVerityEnv, OAuthParam(5.seconds))

  val firstConn = "connId1"
  var firstInvitation: Invitation = _

  "IssuerSDK" - {

    "when tried to setup issuer" - {
      "should be successful" in {
        issuerSDK.fetchAgencyKey()
        issuerSDK.provisionVerityEdgeAgent()
        issuerSDK.registerWebhook()
      }
    }

    "when tried to update com method with unsupported authentication type" - {
      "should respond with error" in {
        val ex = intercept[IllegalArgumentException] {
          issuerSDK.registerWebhook(
            Option(
              ComMethodAuthentication(
                "OAuth1",
                "v1",
                Map(
                  "url" -> "url",
                  "grant_type" -> "client_credentials",
                  "client_id" -> "client_id",
                  "client_secret" -> "client_secret"
                )
              )
            )
          )
        }
        ex.getMessage.contains("authentication type not supported") shouldBe true
      }
    }

    "when tried to update com method with unsupported authentication version" - {
      "should respond with error" in {
        val ex = intercept[IllegalArgumentException] {
          issuerSDK.registerWebhook(
            Option(
              ComMethodAuthentication(
                "OAuth2",
                "v2",
                Map(
                  "url" -> "url",
                  "grant_type" -> "client_credentials",
                  "client_id" -> "client_id",
                  "client_secret" -> "client_secret"
                )
              )
            )
          )
        }
        ex.getMessage.contains("authentication version not supported") shouldBe true
      }
    }

    "when tried to update com method without sufficient data" - {
      "should respond with error" in {
        val ex = intercept[IllegalArgumentException] {
          issuerSDK.registerWebhook(
            Option(
              ComMethodAuthentication(
                "OAuth2",
                "v1",
                Map(
                  "url" -> "url",
                  "client_id" -> "client_id",
                  "client_secret" -> "client_secret"
                )
              )
            )
          )
        }
        ex.getMessage.contains("authentication data required fields missing or invalid") shouldBe true
      }
    }

    "when tried to update com method with empty data for required fields" - {
      "should respond with error" in {
        val ex = intercept[IllegalArgumentException] {
          issuerSDK.registerWebhook(
            Option(
              ComMethodAuthentication(
                "OAuth2",
                "v1",
                Map(
                  "url" -> "url",
                  "grant_type" -> "",
                  "client_id" -> "client_id",
                  "client_secret" -> "client_secret"
                )
              )
            )
          )
        }
        ex.getMessage.contains("authentication data required fields missing or invalid") shouldBe true
      }
    }
  }

  "HolderSDK" - {

    "when tried to setup holder" - {
      "should be successful" in {
        holderSDK.fetchAgencyKey()
        holderSDK.provisionVerityCloudAgent()
      }
    }

    //as this authentication feature is only valid for VAS for now
    "when tried to update com method with authentication data" - {
      "should fail with appropriate error" in {
        val authentication =
          ComMethodAuthentication(
            "OAuth2",
            "v1",
            Map(
              "url" -> "http://www.token.webhook.com",
              "grant_type" -> "client_credentials",
              "client_id" -> "client_id",           //dummy data
              "client_secret" -> "client_secret"    //dummy data
            )
          )
        val updateComMethod = UpdateComMethodReqMsg(
          ComMethod("1", COM_METHOD_TYPE_HTTP_ENDPOINT, "http://www.webhook.com", None, Option(authentication)))
        val ex = intercept[IllegalArgumentException] {
          holderSDK.registerWebhook(updateComMethod)
        }
        ex.getMessage.contains("authentication not supported") shouldBe true
      }
    }
  }

}