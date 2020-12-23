package com.evernym.verity.vault

import com.evernym.verity.actor.wallet._
import com.evernym.verity.ExecutionContextProvider.futureExecutionContext
import com.evernym.verity.ledger.LedgerRequest
import com.evernym.verity.libindy.wallet.api.FutureConverter
import com.evernym.verity.logging.LoggingUtil.getLoggerByClass
import com.evernym.verity.protocol.engine.{DID, VerKey}
import com.evernym.verity.vault.service._
import com.typesafe.scalalogging.Logger
import org.hyperledger.indy.sdk.anoncreds.Anoncreds
import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults.{IssuerCreateAndStoreCredentialDefResult, IssuerCreateSchemaResult}

import scala.language.implicitConversions
import scala.concurrent.Future


class WalletAPI(walletService: WalletService, walletProvider: WalletProvider)
  extends FutureConverter
    with AsyncToSync {

  val logger: Logger = getLoggerByClass(classOf[WalletAPI])

  def signLedgerRequest(slr: SignLedgerRequest): Future[LedgerRequest] = {
    val walletId = slr.submitterDetail.wap
      .getOrElse(throw new Exception("signed requests require wallet info")) // TODO make a better exception
      .walletId
    walletService.executeAsync(walletId, slr).mapTo[LedgerRequest]
  }

  def createWallet(wap: WalletAPIParam): Unit = {
    walletService.executeSync[WalletCreatedBase](wap.walletId, CreateWallet)
  }

  def generateWalletKey(seedOpt: Option[String] = None): String =
    walletProvider.generateKey(seedOpt)

  def createNewKey(cnk: CreateNewKey=CreateNewKey())(implicit wap: WalletAPIParam):
  NewKeyCreated = {
    walletService.executeSync[NewKeyCreated](wap.walletId, cnk)
  }

  def createDID(keyType: String)(implicit wap: WalletAPIParam): NewKeyCreated = {
    walletService.executeSync[NewKeyCreated](wap.walletId, CreateDID(keyType))
  }

  def storeTheirKey(stk: StoreTheirKey)(implicit wap: WalletAPIParam): TheirKeyCreated = {
    walletService.executeSync[TheirKeyCreated](wap.walletId, stk)
  }

  def getVerKeyOption(gvkOpt: GetVerKeyOpt)(implicit wap: WalletAPIParam): Option[VerKey] = {
    walletService.executeSync[Option[VerKey]](wap.walletId, gvkOpt)
  }

  def getVerKey(gvk: GetVerKey)(implicit wap: WalletAPIParam): VerKey = {
    walletService.executeSync[VerKey](wap.walletId, gvk)
  }

  def signMsg(sm: SignMsg)(implicit wap: WalletAPIParam): Array[Byte] = {
    walletService.executeSync[Array[Byte]](wap.walletId, sm)
  }

  def verifySig(vs: VerifySigByKeyInfo)(implicit wap: WalletAPIParam): VerifySigResult = {
    walletService.executeSync[VerifySigResult](wap.walletId, vs)
  }

  def LEGACY_pack(msg: Array[Byte], recipVerKeys: Set[KeyInfo], senderVerKey: Option[KeyInfo])
                 (implicit wap: WalletAPIParam): PackedMsg = {
    walletService.executeSync[PackedMsg](wap.walletId, LegacyPackMsg(msg, recipVerKeys, senderVerKey))
  }

  def LEGACY_unpack(msg: Array[Byte], fromVerKey: Option[KeyInfo], isAnonCryptedMsg: Boolean)
                 (implicit wap: WalletAPIParam): UnpackedMsg = {
    walletService.executeSync[UnpackedMsg](wap.walletId, LegacyUnpackMsg(msg, fromVerKey, isAnonCryptedMsg))
  }

  def LEGACY_unpackAsync(msg: Array[Byte], fromVerKey: Option[KeyInfo], isAnonCryptedMsg: Boolean)
                   (implicit wap: WalletAPIParam): Future[UnpackedMsg] = {
    walletService
      .executeAsync(wap.walletId, LegacyUnpackMsg(msg, fromVerKey, isAnonCryptedMsg))
      .mapTo[UnpackedMsg]
  }

  def packMessage(msg: Array[Byte], recipVerKeys: Set[KeyInfo], senderVerKey: Option[KeyInfo])
                 (implicit wap: WalletAPIParam): PackedMsg = {
    walletService.executeSync[PackedMsg](wap.walletId, PackMsg(msg, recipVerKeys, senderVerKey))
  }

  def unpackMessage(msg: Array[Byte])(implicit wap: WalletAPIParam): UnpackedMsg = {
    walletService.executeSync[UnpackedMsg](wap.walletId, UnpackMsg(msg))
  }

  def unpackMessageAsync(msg: Array[Byte])(implicit wap: WalletAPIParam): Future[UnpackedMsg] = {
    walletService
      .executeAsync(wap.walletId, UnpackMsg(msg))
      .mapTo[UnpackedMsg]
  }

  def createMasterSecret(masterSecretId: String)(implicit wap: WalletAPIParam): String = {
    walletService.executeSync[String](wap.walletId, CreateMasterSecret(masterSecretId))
  }

  def createCredDef(issuerDID: DID,
                    schemaJson: String,
                    tag: String,
                    sigType: Option[String],
                    revocationDetails: Option[String])
                   (implicit wap: WalletAPIParam): IssuerCreateAndStoreCredentialDefResult = {
    walletService.executeSync[IssuerCreateAndStoreCredentialDefResult](wap.walletId,
      CreateCredDef(issuerDID, schemaJson, tag, sigType, revocationDetails))
  }

  def createCredOffer(credDefId: String)(implicit wap: WalletAPIParam): String = {
    walletService.executeSync[String](wap.walletId, CreateCredOffer(credDefId))
  }

  def createCredReq(credDefId: String, proverDID: DID, credDefJson: String, credOfferJson: String, masterSecretId: String)
                   (implicit wap: WalletAPIParam): String = {
    walletService.executeSync[String](wap.walletId,
      CreateCredReq(credDefId, proverDID, credDefJson, credOfferJson, masterSecretId))
  }

  def createCred(credOfferJson: String, credReqJson: String, credValuesJson: String,
                 revRegistryId: String, blobStorageReaderHandle: Int)
                (implicit wap: WalletAPIParam): String = {
    walletService.executeSync[String](wap.walletId,
      CreateCred(credOfferJson, credReqJson, credValuesJson,
        revRegistryId, blobStorageReaderHandle))
  }

  def credentialsForProofReq(proofRequest: String)(implicit wap: WalletAPIParam): String = {
    walletService.executeSync[String](wap.walletId, CredForProofReq(proofRequest))
  }

  def createProof(proofRequest: String, usedCredentials: String, schemas: String,
                  credentialDefs: String, revStates: String, masterSecret: String)
                 (implicit wap: WalletAPIParam): String= {
    walletService.executeSync[String](wap.walletId,
      CreateProof(proofRequest, usedCredentials, schemas,
        credentialDefs, revStates, masterSecret))
  }

  //no wallet needed
  def verifySigWithVerKey(vs: VerifySigByVerKey): VerifySigResult = {
    convertToSyncReq(WalletMsgHandler.coreVerifySig(vs.verKey, vs.challenge, vs.signature))
  }

  //no wallet needed
  def createSchema(issuerDID: DID, name:String, version: String, data: String): IssuerCreateSchemaResult = {
    convertToSyncReq {
      asScalaFuture {
        Anoncreds.issuerCreateSchema(issuerDID, name, version, data)
      }
    }
  }

  //no wallet needed
  def verifyProof(proofRequest: String, proof: String, schemas: String, credentialDefs: String,
                  revocRegDefs: String, revocRegs: String): Boolean = {
    convertToSyncReq {
      asScalaFuture {
        Anoncreds.verifierVerifyProof(proofRequest, proof, schemas, credentialDefs, revocRegDefs, revocRegs)
      }.map(_.booleanValue())
    }
  }
}


