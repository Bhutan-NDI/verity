package com.evernym.verity.actor.wallet

import java.util.UUID

import akka.testkit.ImplicitSender
import com.evernym.verity.actor.agentRegion
import com.evernym.verity.actor.testkit.ActorSpec
import com.evernym.verity.testkit.BasicSpec
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.Eventually

class WalletActorPassivationSpec
  extends ActorSpec
    with BasicSpec
    with ImplicitSender
    with Eventually {

  lazy val walletActorEntityId: String = UUID.randomUUID().toString
  lazy val walletActor: agentRegion = agentRegion(walletActorEntityId, walletRegionActor)

  val PASSIVATE_TIMEOUT_IN_SECONDS = 15

  "Wallet Actor" - {
    "when sent CreateWallet command" - {
      "should respond with WalletCreated" in {
        walletActor ! CreateWallet
        expectMsgType[WalletCreated.type]
      }
    }

    "when waited for more than passivate time" - {
      "should be stopped" in {
        val prevWalletActorRef = lastSender
        Thread.sleep((PASSIVATE_TIMEOUT_IN_SECONDS * 1000) + 10000)
        walletActor ! CreateNewKey()
        expectMsgType[NewKeyCreated]
        lastSender should not be prevWalletActorRef //this indirectly proves actor got restarted
      }
    }
  }

  override def overrideConfig: Option[Config] = Option {
    val confStr = s"verity.sharded-actor-passivate-time.WalletActor.passivate-time-in-seconds = 15"
    ConfigFactory.parseString(confStr)
  }
}
