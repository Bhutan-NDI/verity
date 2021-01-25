package com.evernym.verity.actor.testkit.actor

import akka.actor.{ActorRef, ActorSystem}
import com.evernym.verity.actor.StorageInfo
import com.evernym.verity.actor.agent.{AgentActorContext, WalletApiBuilder}
import com.evernym.verity.actor.agent.msgrouter.AgentMsgRouter
import com.evernym.verity.agentmsg.msgpacker.AgentMsgTransformer
import com.evernym.verity.config.AppConfig
import com.evernym.verity.ledger.{LedgerPoolConnManager, LedgerSvc}
import com.evernym.verity.protocol
import com.evernym.verity.protocol.actor.ActorDriverGenParam
import com.evernym.verity.protocol.engine.{PinstIdResolution, ProtocolRegistry}
import com.evernym.verity.protocol.protocols.tictactoe.TicTacToeProtoDef
import com.evernym.verity.storage_services.aws_s3.StorageAPI
import com.evernym.verity.testkit.mock.ledger.InMemLedgerPoolConnManager
import com.evernym.verity.texter.SMSSender

import scala.concurrent.Future
import com.evernym.verity.ExecutionContextProvider.futureExecutionContext
import com.evernym.verity.vault.WalletProvider
import com.evernym.verity.vault.service.WalletService
import com.evernym.verity.vault.wallet_api.WalletAPI

/**
 *
 * @param system
 * @param appConfig pre built config object
 */
class MockAgentActorContext(val system: ActorSystem,
                            val appConfig: AppConfig,
                            mockParam: MockAgentActorContextParam = MockAgentActorContextParam())
  extends AgentActorContext {

  override lazy val smsSvc: SMSSender = new MockSMSSender(appConfig)
  override lazy val ledgerSvc: LedgerSvc = new MockLedgerSvc(system)

  override lazy val msgSendingSvc: MsgSendingSvcType = MockMsgSendingSvc
  override lazy val poolConnManager: LedgerPoolConnManager = new InMemLedgerPoolConnManager()(system.dispatcher)
  override lazy val agentMsgRouter: AgentMsgRouter = new MockAgentMsgRouter(mockParam.actorTypeToRegions)(appConfig, system)
  override lazy val agentMsgTransformer: AgentMsgTransformer = new AgentMsgTransformer(walletAPI)

  override lazy val s3API: StorageAPI = new StorageAPI {
    var S3Mock: Map[String, Array[Byte]] = Map()
    override def upload(id: String, data: Array[Byte]): Future[StorageInfo] = {
      S3Mock += (id -> data)
      Future { StorageInfo("https://s3-us-west-2.amazonaws.com", "test-bucket") }
    }

    override def download(id: String): Future[Array[Byte]] = {
      Future { S3Mock(id) }
    }
  }

  override lazy val protocolRegistry: ProtocolRegistry[ActorDriverGenParam] =
    ProtocolRegistry(protocol.protocols.protocolRegistry.entries :+
      ProtocolRegistry.Entry(TicTacToeProtoDef, PinstIdResolution.V0_2): _*)

  override def buildWalletAPI(appConfig: AppConfig,
                              walletService: WalletService,
                              walletProvider: WalletProvider,
                              poolConnManager: Option[LedgerPoolConnManager]=None): WalletAPI = {
    //this is for unit test to not depend on pool ledger
    WalletApiBuilder.createWalletAPI(appConfig, walletService, walletProvider, None)
  }
}

case class MockAgentActorContextParam(actorTypeToRegions: Map[Int, ActorRef]=Map.empty)