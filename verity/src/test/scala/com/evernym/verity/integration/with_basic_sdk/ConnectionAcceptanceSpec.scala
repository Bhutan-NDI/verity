package com.evernym.verity.integration.with_basic_sdk


import akka.http.scaladsl.model.StatusCodes.Unauthorized
import com.evernym.verity.agentmsg.msgfamily.ConfigDetail
import com.evernym.verity.agentmsg.msgfamily.configs.UpdateConfigReqMsg
import com.evernym.verity.integration.base.{CAS, VAS, VerityProviderBaseSpec}
import com.evernym.verity.integration.base.sdk_provider.SdkProvider
import com.evernym.verity.did.didcomm.v1.{Thread => MsgThread}
import com.evernym.verity.actor.testkit.TestAppConfig
import com.evernym.verity.protocol.protocols.relationship.v_1_0.Signal.Invitation
import com.evernym.verity.util2.ExecutionContextProvider
import com.evernym.verity.util.TestExecutionContextProvider

import scala.concurrent.ExecutionContext


class ConnectionAcceptanceSpec
  extends VerityProviderBaseSpec
    with SdkProvider {

  lazy val ecp = TestExecutionContextProvider.ecp
  lazy val executionContext: ExecutionContext = ecp.futureExecutionContext

  lazy val issuerVerityEnv = VerityEnvBuilder.default().build(VAS)
  lazy val holderVerityEnv = VerityEnvBuilder.default().build(CAS)

  lazy val issuerSDK = setupIssuerSdk(issuerVerityEnv, executionContext, ecp.walletFutureExecutionContext)

  lazy val holderSDK1 = setupHolderSdk(holderVerityEnv, defaultSvcParam.ledgerTxnExecutor, executionContext, ecp.walletFutureExecutionContext)
  lazy val holderSDK2 = setupHolderSdk(holderVerityEnv, defaultSvcParam.ledgerTxnExecutor, executionContext, ecp.walletFutureExecutionContext)

  override def beforeAll(): Unit = {
    super.beforeAll()

    issuerSDK.fetchAgencyKey()
    issuerSDK.provisionVerityEdgeAgent()
    issuerSDK.registerWebhook()
    issuerSDK.sendUpdateConfig(UpdateConfigReqMsg(Set(ConfigDetail("name", "issuer-name"), ConfigDetail("logoUrl", "issuer-logo-url"))))
  }

  val firstConn = "connId1"
  var firstInvitation: Invitation = _
  var lastReceivedThread: Option[MsgThread] = None

  "IssuerSDK" - {
    "when sent 'create' (relationship 1.0) message" - {
      "should be successful" in {
        val receivedMsg = issuerSDK.sendCreateRelationship(firstConn)
        val created = receivedMsg.msg
        created.did.nonEmpty shouldBe true
        created.verKey.nonEmpty shouldBe true
        lastReceivedThread = receivedMsg.threadOpt
      }
    }

    "when sent 'connection-invitation' (relationship 1.0) message" - {
      "should be successful" in {
        val invitation = issuerSDK.sendCreateConnectionInvitation(firstConn, lastReceivedThread)
        invitation.inviteURL.nonEmpty shouldBe true
        firstInvitation = invitation
      }
    }
  }

  "HolderSDK1" - {

    "when provisioned cloud agent" - {
      "should be successful" in {
        holderSDK1.fetchAgencyKey()
        val created = holderSDK1.provisionVerityCloudAgent()
        created.selfDID.nonEmpty shouldBe true
        created.agentVerKey.nonEmpty shouldBe true
      }
    }

    "when accepting first invitation" - {
      "should be successful" in {
        holderSDK1.sendCreateNewKey(firstConn)
        holderSDK1.sendConnReqForInvitation(firstConn, firstInvitation)
      }
    }
  }

  "IssuerSDK" - {
    "should receive final 'complete' (connections 1.0) message" in {
      val complete = issuerSDK.expectConnectionComplete(firstConn)
      complete.theirDid.isEmpty shouldBe false
    }
  }

  "HolderSDK2" - {

    "when provisioned cloud agent" - {
      "should be successful" in {
        holderSDK2.fetchAgencyKey()
        val created = holderSDK2.provisionVerityCloudAgent()
        created.selfDID.nonEmpty shouldBe true
        created.agentVerKey.nonEmpty shouldBe true
      }
    }

    "when try to accept first invitation (already accepted one)" - {
      "should fail with Unauthorized error" in {
        holderSDK2.sendCreateNewKey(firstConn)
        val httpResp = holderSDK2.sendConnReqForAcceptedInvitation(firstConn, firstInvitation)
        httpResp.status shouldBe Unauthorized
      }
    }
  }

  /**
   * custom thread pool executor
   */
  override def futureExecutionContext: ExecutionContext = executionContext

  override def executionContextProvider: ExecutionContextProvider = ecp
}
