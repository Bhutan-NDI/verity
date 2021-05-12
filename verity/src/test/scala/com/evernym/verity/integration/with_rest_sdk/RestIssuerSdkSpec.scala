package com.evernym.verity.integration.with_rest_sdk

import akka.http.scaladsl.model.StatusCodes.Accepted
import com.evernym.verity.integration.base.VerityProviderBaseSpec
import com.evernym.verity.integration.base.sdk_provider.SdkProvider
import com.evernym.verity.protocol.engine.ThreadId
import com.evernym.verity.protocol.protocols.connecting.common.ConnReqReceived
import com.evernym.verity.protocol.protocols.connections.v_1_0.Signal.{Complete, ConnResponseSent}
import com.evernym.verity.protocol.protocols.issuersetup.v_0_6.{Create, PublicIdentifierCreated}
import com.evernym.verity.protocol.protocols.questionAnswer.v_1_0.Ctl.AskQuestion
import com.evernym.verity.protocol.protocols.questionAnswer.v_1_0.Msg.{Answer, Question}
import com.evernym.verity.protocol.protocols.questionAnswer.v_1_0.QuestionAnswerMsgFamily
import com.evernym.verity.protocol.protocols.questionAnswer.v_1_0.Signal.{AnswerGiven, StatusReport => QAStatusReport}
import com.evernym.verity.protocol.protocols.relationship.v_1_0.Ctl.ConnectionInvitation
import com.evernym.verity.protocol.protocols.relationship.v_1_0.Signal.Invitation
import com.evernym.verity.protocol.protocols.updateConfigs.v_0_6.{ConfigResult, Update, Config => AgentConfig}
import com.evernym.verity.protocol.protocols.writeSchema.v_0_6.{Write, StatusReport => WSStatusReport}
import com.typesafe.config.{Config, ConfigFactory}

import java.util.UUID


class RestIssuerSdkSpec
  extends VerityProviderBaseSpec
    with SdkProvider {

  lazy val issuerVerityApp = setupNewVerityApp(overriddenConfig = VAS_OVERRIDE_CONFIG)
  lazy val holderVerityApp = setupNewVerityApp()

  lazy val issuerRestSDK = setupIssuerRestSdk(issuerVerityApp)
  lazy val holderSDK = setupHolderSdk(holderVerityApp, defaultSvcParam.ledgerSvcParam.ledgerTxnExecutor)

  override def beforeAll(): Unit = {
    super.beforeAll()
    issuerRestSDK.fetchAgencyKey()
    issuerRestSDK.provisionVerityEdgeAgent()    //this sends a packed message (not REST api call)
    issuerRestSDK.registerWebhook()             //this sends a packed message (not REST api call)
  }

  var lastThreadId: Option[ThreadId] = None
  val firstConn = "connId1"
  var firstInvitation: Invitation = _

  "IssuerRestSdk" - {
    "when sent POST update (update config 0.6) message" - {
      "should be successful" in {
        val lastThreadId = Option(UUID.randomUUID().toString)
        val msg = Update(Set(AgentConfig("name", "env-name"), AgentConfig("logoUrl", "env-logo-url")))
        val response = issuerRestSDK.sendRestReq(msg, lastThreadId)
        response.status shouldBe Accepted
        val receivedMsgParam = issuerRestSDK.expectMsgOnWebhook[ConfigResult]()
        receivedMsgParam.msg.configs.size shouldBe 2
      }
    }

    "when sent GET status (update config 0.6) message" - {
      "should be successful" in {
        val configResult = issuerRestSDK.sendGetStatusReq[ConfigResult](lastThreadId)
        configResult.status shouldBe "OK"
        configResult.result.configs.size shouldBe 2
      }
    }

    "when sent POST create (issuer-setup 0.6) message" - {
      "should be successful" in {
        val msg = Create()
        val response = issuerRestSDK.sendRestReq(msg)
        response.status shouldBe Accepted

        val receivedMsgParam = issuerRestSDK.expectMsgOnWebhook[PublicIdentifierCreated]()
        receivedMsgParam.msg.identifier.did.nonEmpty shouldBe true
        receivedMsgParam.msg.identifier.verKey.nonEmpty shouldBe true
      }
    }

    "when sent POST write (write-schema 0.6) message" - {
      "should be successful" in {
        val msg = Write("schema-name", "1.0", Seq("firstName","lastName"))
        val response = issuerRestSDK.sendRestReq(msg)
        response.status shouldBe Accepted
        val receivedMsgParam = issuerRestSDK.expectMsgOnWebhook[WSStatusReport]()
        receivedMsgParam.msg.schemaId.nonEmpty shouldBe true
      }
    }

    "when sent POST create (relationship 1.0) message" - {
      "should be successful" in {
        val receivedMsgParam = issuerRestSDK.createRelationship(firstConn)
        lastThreadId = receivedMsgParam.threadIdOpt
        receivedMsgParam.msg.did.nonEmpty shouldBe true
        receivedMsgParam.msg.verKey.nonEmpty shouldBe true
      }
    }

    "when sent POST connection-invitation (relationship 1.0) message" - {
      "should be successful" in {
        val msg = ConnectionInvitation()
        val response = issuerRestSDK.sendRestReq(msg, lastThreadId)
        response.status shouldBe Accepted
        val receivedMsgParam = issuerRestSDK.expectMsgOnWebhook[Invitation]()
        receivedMsgParam.msg.inviteURL.nonEmpty shouldBe true
        firstInvitation = receivedMsgParam.msg
      }
    }
  }

  "HolderSDK" - {

    "when provisioned cloud agent" - {
      "should be successful" in {
        holderSDK.fetchAgencyKey()
        val created = holderSDK.provisionVerityCloudAgent()
        created.selfDID.nonEmpty shouldBe true
        created.agentVerKey.nonEmpty shouldBe true
      }
    }

    "when accepting first invitation" - {
      "should be successful" in {
        holderSDK.sendCreateNewKey(firstConn)
        holderSDK.sendConnReqForInvitation(firstConn, firstInvitation)
      }
    }
  }

  "IssuerSdk" - {
    "after user accepted invitation" - {
      "should receive notifications on webhook" in {
        issuerRestSDK.expectMsgOnWebhook[ConnReqReceived]()
        issuerRestSDK.expectMsgOnWebhook[ConnResponseSent]()
        val receivedMsgParam = issuerRestSDK.expectMsgOnWebhook[Complete]()
        receivedMsgParam.msg.theirDid.isEmpty shouldBe false
      }
    }

    "when sent ask-question (questionanswer 1.0) message" - {
      "should be successful" in {
        val msg = AskQuestion("How are you?", Option("question-detail"),
          Vector("I am fine","I am not fine"), signature_required = false, None)
        val response = issuerRestSDK.sendRestReqForConn(firstConn, msg)
        response.status shouldBe Accepted
      }
    }
  }

  "HolderSDK" - {
    "when tried to get newly un viewed messages" - {
      "should get 'question' (questionanswer 1.0) message" in {
        val receivedMsgParam = holderSDK.expectMsgFromConn[Question](firstConn)
        lastThreadId = receivedMsgParam.threadIdOpt
        val question = receivedMsgParam.msg
        question.question_text shouldBe "How are you?"
      }
    }

    "when sent 'answer' (questionanswer 1.0) message" - {
      "should be successful" in {
        val answer = Answer("I am fine", None, None)
        holderSDK.sendProtoMsgToTheirAgent(firstConn, answer, lastThreadId)
      }
    }
  }

  "IssuerSdk" - {
    "when tried to get newly un viewed messages" - {
      "should get 'answer' (questionanswer 1.0) message" in {
        val receivedMsgParam = issuerRestSDK.expectMsgOnWebhook[AnswerGiven]()
        receivedMsgParam.msg.answer shouldBe "I am fine"
      }
    }

    "when sent GET status (questionanswer 1.0)" - {
      "should be successful" in {
        val restOkResp = issuerRestSDK.sendGetStatusReqForConn[QAStatusReport](firstConn, QuestionAnswerMsgFamily, lastThreadId)
        restOkResp.status shouldBe "OK"
      }
    }
  }

  val VAS_OVERRIDE_CONFIG: Option[Config] = Option {
    ConfigFactory.parseString(
      """
         verity.rest-api.enabled = true
        """.stripMargin
    )
  }
}