package com.evernym.verity.vault

import akka.actor.ActorSystem
import com.evernym.verity.actor.agent.WalletApiBuilder
import com.evernym.verity.actor.testkit.{CommonSpecUtil, TestAppConfig}
import com.evernym.verity.actor.wallet.{CreateNewKey, GetVerKey, NewKeyCreated, StoreTheirKey, TheirKeyStored}
import com.evernym.verity.config.AppConfig
import com.evernym.verity.ledger.LedgerPoolConnManager
import com.evernym.verity.libindy.ledger.IndyLedgerPoolConnManager
import com.evernym.verity.libindy.wallet.LibIndyWalletProvider
import com.evernym.verity.protocol.engine.WalletAccess.KEY_ED25519
import com.evernym.verity.testkit.BasicSpecWithIndyCleanup
import com.evernym.verity.testkit.util.TestUtil
import com.evernym.verity.vault.service.ActorWalletService
import com.evernym.verity.vault.wallet_api.WalletAPI


class WalletAPISpec extends BasicSpecWithIndyCleanup with CommonSpecUtil {

  lazy val config:AppConfig = new TestAppConfig()
  lazy val poolConnManager: LedgerPoolConnManager =  new IndyLedgerPoolConnManager(config)
  lazy val walletProvider: LibIndyWalletProvider = new LibIndyWalletProvider(config)
  lazy val walletService = new ActorWalletService(ActorSystem())
  implicit lazy val walletAPI: WalletAPI = WalletApiBuilder.build(config, TestUtil, walletService, walletProvider, poolConnManager)

  lazy val aliceWap: WalletAPIParam = createWallet("alice", walletAPI)
  lazy val bobWap: WalletAPIParam = createWallet("bob", walletAPI)

  var aliceKey: NewKeyCreated = _
  var bobKey: NewKeyCreated = _

  val msg: String = "hi there"

  "Wallet API" - {
    "when asked to create new key in alice's wallet" - {
      "should create key successfully" in {
        aliceKey = walletAPI.createNewKey(CreateNewKey())(aliceWap)
        aliceKey shouldBe a[NewKeyCreated]
      }
    }

    "when asked to create new key in bob's wallet" - {
      "should create key successfully" in {
        bobKey = walletAPI.createNewKey(CreateNewKey())(bobWap)
        bobKey shouldBe a[NewKeyCreated]
      }
    }

    "when asked to store bob's key in alice's wallet" - {
      "should store their key successfully" in {
        val response = walletAPI.storeTheirKey(StoreTheirKey(bobKey.did, bobKey.verKey))(aliceWap)
        response shouldBe a[TheirKeyStored]
        val responseVerKey = walletAPI.getVerKey(
          GetVerKey(KeyInfo(Right(GetVerKeyByDIDParam(bobKey.did, getKeyFromPool = false)))))(aliceWap)
        responseVerKey shouldBe bobKey.verKey
      }
    }

    "when asked to store alice's key in bob's wallet" - {
      "should store their key successfully" in {
        val response = walletAPI.storeTheirKey(StoreTheirKey(aliceKey.did, aliceKey.verKey))(bobWap)
        response shouldBe a[TheirKeyStored]
        val responseVerKey = walletAPI.getVerKey(
          GetVerKey(KeyInfo(Right(GetVerKeyByDIDParam(aliceKey.did, getKeyFromPool = false)))))(bobWap)
        responseVerKey shouldBe aliceKey.verKey
      }
    }

    "when asked to store other's key with fully qualified DID" - {
      "should store their key successfully" in {
        val response = walletAPI.storeTheirKey(StoreTheirKey("did:sov:NcysrVCeLU1WNdJdLYxU6g", "CnToPx3rPHNaXkMMtdPTnsK45pSHvP1e4BzNrk3oSVgr"))(bobWap)
        response shouldBe a[TheirKeyStored]

      }
    }
    "when asked to create new DID" - {
      "should be successful" in {
        val testWap: WalletAPIParam = createWallet("test", walletAPI)
        val response = walletAPI.createDID(KEY_ED25519)(testWap)
        response shouldBe a[NewKeyCreated]
      }
    }
  }

}
