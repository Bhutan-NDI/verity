package com.evernym.verity.actor

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKitBase}
import com.evernym.verity.actor.testkit.AkkaTestBasic
import com.evernym.verity.actor.testkit.actor.ProvidesMockPlatform
import com.evernym.verity.testkit.BasicSpec
import org.scalatest.concurrent.Eventually
import com.evernym.verity.actor.wallet.{CreateNewKey, CreateWallet, DeleteWallet, WalletDeleted}
import com.evernym.verity.vault.NewKeyCreated
class WalletActorSpec extends TestKitBase
  with ProvidesMockPlatform
  with BasicSpec
  with ImplicitSender
  with Eventually {

  implicit lazy val system: ActorSystem = AkkaTestBasic.system()
  lazy val walletActorEntityId: String = UUID.randomUUID().toString
  lazy val walletActor: agentRegion = agentRegion(walletActorEntityId, walletRegionActor)

  "WalletActor" - {

    "creating WalletActor" - {
      "should create and open wallet" in {
        walletActor ! CreateNewKey
        val keys = expectMsgType[NewKeyCreated]
        walletActor ! CreateWallet(keys.did, keys.verKey)
        expectMsgType[WalletDeleted.type]

      }
    }
  }


}
