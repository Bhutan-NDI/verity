package com.evernym.verity.integration.with_basic_sdk

import com.evernym.verity.agentmsg.msgfamily.ConfigDetail
import com.evernym.verity.agentmsg.msgfamily.configs.UpdateConfigReqMsg
import com.evernym.verity.integration.base.VerityProviderBaseSpec
import com.evernym.verity.integration.base.sdk_provider.SdkProvider
import com.evernym.verity.protocol.engine.ThreadId
import com.evernym.verity.protocol.protocols.connecting.common.ConnReqReceived
import com.evernym.verity.protocol.protocols.connections.v_1_0.Signal.{Complete, ConnResponseSent}
import com.evernym.verity.protocol.protocols.questionAnswer.v_1_0.Ctl.AskQuestion
import com.evernym.verity.protocol.protocols.questionAnswer.v_1_0.Msg.{Answer, Question}
import com.evernym.verity.protocol.protocols.questionAnswer.v_1_0.Signal.AnswerGiven
import com.evernym.verity.protocol.protocols.relationship.v_1_0.Signal.Invitation


class QuestionAnswerSpec
  extends VerityProviderBaseSpec
    with SdkProvider {

  lazy val issuerVerityApp = setupNewVerityApp()
  lazy val holderVerityApp = setupNewVerityApp()

  lazy val issuerSDK = setupIssuerSdk(issuerVerityApp)
  lazy val holderSDK = setupHolderSdk(holderVerityApp, defaultSvcParam.ledgerSvcParam.ledgerTxnExecutor)

  val firstConn = "connId1"
  var firstInvitation: Invitation = _

  override def beforeAll(): Unit = {
    super.beforeAll()

    issuerSDK.fetchAgencyKey()
    issuerSDK.provisionVerityEdgeAgent()
    issuerSDK.registerWebhook()
    issuerSDK.sendUpdateConfig(UpdateConfigReqMsg(Set(ConfigDetail("name", "issuer-name"), ConfigDetail("logoUrl", "issuer-logo-url"))))
    val receivedMsg = issuerSDK.sendCreateRelationship(firstConn)
    firstInvitation = issuerSDK.sendCreateConnectionInvitation(firstConn, receivedMsg.threadIdOpt)

    holderSDK.fetchAgencyKey()
    holderSDK.provisionVerityCloudAgent()
    holderSDK.sendCreateNewKey(firstConn)
    holderSDK.sendConnReqForInvitation(firstConn, firstInvitation)

    issuerSDK.expectMsgOnWebhook[ConnReqReceived]()
    issuerSDK.expectMsgOnWebhook[ConnResponseSent]()
    issuerSDK.expectMsgOnWebhook[Complete]()
  }

  "IssuerSDK" - {
    "when tried to send 'ask-question' (questionanswer 1.0) message" - {
      "should be successful" in {
        issuerSDK.sendControlMsgForConn(firstConn, AskQuestion("How are you?", Option("detail"), Vector("I am fine", "I am not fine"), signature_required = false, None))
      }
    }
  }

  var lastReceivedMsgThreadId: Option[ThreadId] = None

  "HolderSDK" - {
    "when tried to get newly un viewed messages" - {
      "should get 'question' (questionanswer 1.0) message" in {
        val receivedMsg = holderSDK.expectMsgFromConn[Question](firstConn)
        lastReceivedMsgThreadId = receivedMsg.threadIdOpt
        val question = receivedMsg.msg
        question.question_text shouldBe "How are you?"
        holderSDK.sendUpdateMsgStatusAsReviewedForConn(firstConn, receivedMsg.msgId)
      }
    }

    "when tried to respond with 'answer' (questionanswer 1.0) message" - {
      "should be successful" in {
        val answer = Answer("I am fine", None, None)
        holderSDK.sendProtoMsgToTheirAgent(firstConn, answer, lastReceivedMsgThreadId)
      }
    }
  }

  "IssuerSDK" - {
    "should receive 'answer-given' (questionanswer 1.0) message" in {
      val receivedMsg = issuerSDK.expectMsgOnWebhook[AnswerGiven]()
      receivedMsg.msg.answer shouldBe "I am fine"
    }
  }

  "VerityAdmin" - {
    "when restarts verity instances" - {
      "should be successful" in {
        issuerVerityApp.restart()
        holderVerityApp.restart()
        issuerSDK.fetchAgencyKey()
        holderSDK.fetchAgencyKey()
      }
    }
  }

  "IssuerSDK" - {
    "when tried to send another 'ask-question' (questionanswer 1.0) message" - {
      "should be successful" in {
        val msg = AskQuestion("How are you after restart?", Option("detail"), Vector("I am fine", "I am not fine"), signature_required = false, None)
        issuerSDK.sendControlMsgForConn(firstConn, msg)
      }
    }
  }

  "HolderSDK" - {
    "when tried to get newly un viewed messages after restart" - {
      "should get 'question' (questionanswer 1.0) message" in {
        val receivedMsg = holderSDK.expectMsgFromConn[Question](firstConn)
        lastReceivedMsgThreadId = receivedMsg.threadIdOpt
        val question = receivedMsg.msg
        question.question_text shouldBe "How are you after restart?"
      }
    }

    "when tried to respond with 'answer' (questionanswer 1.0) message after restart" - {
      "should be successful" in {
        val answer = Answer("I am fine after restart too", None, None)
        holderSDK.sendProtoMsgToTheirAgent(firstConn, answer, lastReceivedMsgThreadId)
      }
    }
  }

  "IssuerSDK" - {
    "should receive 'answer' (questionanswer 1.0) message after restart" in {
      val receivedMsg = issuerSDK.expectMsgOnWebhook[AnswerGiven]()
      receivedMsg.msg.answer shouldBe "I am fine after restart too"
    }
  }
}