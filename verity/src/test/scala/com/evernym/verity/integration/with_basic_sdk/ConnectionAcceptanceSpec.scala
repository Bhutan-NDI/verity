package com.evernym.verity.integration.with_basic_sdk


import akka.http.scaladsl.model.StatusCodes.Unauthorized
import com.evernym.verity.agentmsg.msgfamily.ConfigDetail
import com.evernym.verity.agentmsg.msgfamily.configs.UpdateConfigReqMsg
import com.evernym.verity.integration.base.VerityProviderBaseSpec
import com.evernym.verity.integration.base.sdk_provider.SdkProvider
import com.evernym.verity.protocol.engine.ThreadId
import com.evernym.verity.protocol.protocols.connecting.common.ConnReqReceived
import com.evernym.verity.protocol.protocols.connections.v_1_0.Signal.{Complete, ConnResponseSent}
import com.evernym.verity.protocol.protocols.relationship.v_1_0.Signal.Invitation


class ConnectionAcceptanceSpec
  extends VerityProviderBaseSpec
    with SdkProvider {

  lazy val issuerVerityApp = setupNewVerityApp()
  lazy val holderVerityApp = setupNewVerityApp()

  lazy val issuerSDK = setupIssuerSdk(issuerVerityApp)

  lazy val holderSDK1 = setupHolderSdk(holderVerityApp, defaultSvcParam.ledgerSvcParam.ledgerTxnExecutor)
  lazy val holderSDK2 = setupHolderSdk(holderVerityApp, defaultSvcParam.ledgerSvcParam.ledgerTxnExecutor)

  override def beforeAll(): Unit = {
    super.beforeAll()

    issuerSDK.fetchAgencyKey()
    issuerSDK.provisionVerityEdgeAgent()
    issuerSDK.registerWebhook()
    issuerSDK.sendUpdateConfig(UpdateConfigReqMsg(Set(ConfigDetail("name", "issuer-name"), ConfigDetail("logoUrl", "issuer-logo-url"))))
  }

  val firstConn = "connId1"
  var firstInvitation: Invitation = _
  var lastReceivedThreadId: Option[ThreadId] = None

  "IssuerSDK" - {
    "when sent 'create' (relationship 1.0) message" - {
      "should be successful" in {
        val receivedMsg = issuerSDK.sendCreateRelationship(firstConn)
        val created = receivedMsg.msg
        created.did.nonEmpty shouldBe true
        created.verKey.nonEmpty shouldBe true
        lastReceivedThreadId = receivedMsg.threadIdOpt
      }
    }

    "when sent 'connection-invitation' (relationship 1.0) message" - {
      "should be successful" in {
        val invitation = issuerSDK.sendCreateConnectionInvitation(firstConn, lastReceivedThreadId)
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
      issuerSDK.expectMsgOnWebhook[ConnReqReceived]()
      issuerSDK.expectMsgOnWebhook[ConnResponseSent]()
      val receivedMsg = issuerSDK.expectMsgOnWebhook[Complete]()
      receivedMsg.msg.theirDid.isEmpty shouldBe false
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
}
