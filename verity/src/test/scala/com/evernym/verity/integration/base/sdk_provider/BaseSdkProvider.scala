package com.evernym.verity.integration.base.sdk_provider

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.model._
import com.evernym.verity.ExecutionContextProvider.futureExecutionContext
import com.evernym.verity.actor.agent.DidPair
import com.evernym.verity.actor.agent.MsgPackFormat.MPF_INDY_PACK
import com.evernym.verity.actor.wallet._
import com.evernym.verity.actor.AgencyPublicDid
import com.evernym.verity.agentmsg.DefaultMsgCodec
import com.evernym.verity.agentmsg.msgcodec.jackson.JacksonMsgCodec
import com.evernym.verity.agentmsg.msgpacker.AgentMsgPackagingUtil
import com.evernym.verity.libindy.wallet.LibIndyWalletProvider
import com.evernym.verity.protocol.engine.{MsgFamily, _}
import com.evernym.verity.protocol.protocols.agentprovisioning.v_0_7.AgentProvisioningMsgFamily
import com.evernym.verity.protocol.protocols.agentprovisioning.v_0_7.AgentProvisioningMsgFamily.AgentCreated
import com.evernym.verity.protocol.protocols.connections.v_1_0.Msg.ConnResponse
import com.evernym.verity.protocol.protocols.connections.v_1_0.Msg
import com.evernym.verity.protocol.protocols.relationship.v_1_0.Signal.Invitation
import com.evernym.verity.protocol.protocols.writeSchema.{v_0_6 => writeSchema0_6}
import com.evernym.verity.protocol.protocols.writeCredentialDefinition.{v_0_6 => writeCredDef0_6}
import com.evernym.verity.testkit.LegacyWalletAPI
import com.evernym.verity.util.Base64Util
import com.evernym.verity.vault.{KeyParam, WalletAPIParam}
import com.evernym.verity.ServiceEndpoint
import com.evernym.verity.actor.testkit.TestAppConfig
import com.evernym.verity.agentmsg.msgfamily.ConfigDetail
import com.evernym.verity.agentmsg.msgfamily.configs.UpdateConfigReqMsg
import com.evernym.verity.integration.base.VerityRuntimeEnv
import com.evernym.verity.ledger.LedgerTxnExecutor
import com.evernym.verity.protocol.protocols
import com.evernym.verity.protocol.protocols.connecting.common.ConnReqReceived
import com.evernym.verity.protocol.protocols.connections.v_1_0.Signal.{Complete, ConnResponseSent}
import com.evernym.verity.protocol.protocols.issuersetup.v_0_6.{Create, PublicIdentifierCreated}
import org.json.JSONObject

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, SECONDS}
import scala.reflect.ClassTag
import scala.util.Try


trait SdkProvider {
  def setupIssuerSdk(verityRuntimeEnv: VerityRuntimeEnv): IssuerSdk = IssuerSdk(buildSdkParam(verityRuntimeEnv))
  def setupVerifierSdk(verityRuntimeEnv: VerityRuntimeEnv): VerifierSdk = VerifierSdk(buildSdkParam(verityRuntimeEnv))
  def setupIssuerRestSdk(verityRuntimeEnv: VerityRuntimeEnv): IssuerRestSDK = IssuerRestSDK(buildSdkParam(verityRuntimeEnv))

  def setupHolderSdk(verityRuntimeEnv: VerityRuntimeEnv, ledgerTxnExecutor: LedgerTxnExecutor): HolderSdk =
    HolderSdk(buildSdkParam(verityRuntimeEnv), ledgerTxnExecutor)

  def buildSdkParam(verityRuntimeEnv: VerityRuntimeEnv): SdkParam = {
    val httpPort = verityRuntimeEnv.platform.appConfig.getConfigIntReq("verity.http.port")
    val verityBaseUrl = s"http://localhost:$httpPort"
    SdkParam(verityBaseUrl)
  }

  def provisionEdgeAgent(sdk: IssuerVerifierSdk): Unit = {
    sdk.fetchAgencyKey()
    sdk.provisionVerityEdgeAgent()
    sdk.registerWebhook()
    sdk.sendUpdateConfig(UpdateConfigReqMsg(Set(ConfigDetail("name", "issuer-name"), ConfigDetail("logoUrl", "issuer-logo-url"))))
  }

  def provisionCloudAgent(holderSDK: HolderSdk): Unit = {
    holderSDK.fetchAgencyKey()
    holderSDK.provisionVerityCloudAgent()
  }

  def establishConnection(connId: String, issuerSDK: IssuerVerifierSdk, holderSDK: HolderSdk): Unit = {
    val receivedMsg = issuerSDK.sendCreateRelationship(connId)
    val lastReceivedThreadId = receivedMsg.threadIdOpt
    val firstInvitation = issuerSDK.sendCreateConnectionInvitation(connId, lastReceivedThreadId)

    holderSDK.sendCreateNewKey(connId)
    holderSDK.sendConnReqForInvitation(connId, firstInvitation)

    issuerSDK.expectMsgOnWebhook[ConnReqReceived]()
    issuerSDK.expectMsgOnWebhook[ConnResponseSent]()
    issuerSDK.expectMsgOnWebhook[Complete]()
  }

  def setupIssuer(issuerSDK: IssuerVerifierSdk): Unit = {
    issuerSDK.sendControlMsg(Create())
    issuerSDK.expectMsgOnWebhook[PublicIdentifierCreated]()
  }

  def writeSchema(issuerSDK: IssuerVerifierSdk, write: writeSchema0_6.Write): SchemaId = {
    issuerSDK.sendControlMsg(write)
    val receivedMsg = issuerSDK.expectMsgOnWebhook[writeSchema0_6.StatusReport]()
    receivedMsg.msg.schemaId
  }

  def writeCredDef(issuerSDK: IssuerVerifierSdk, write: writeCredDef0_6.Write): CredDefId = {
    issuerSDK.sendControlMsg(write)
    val receivedMsg = issuerSDK.expectMsgOnWebhook[writeCredDef0_6.StatusReport](Duration(30, SECONDS))
    receivedMsg.msg.credDefId
  }

  type SchemaId = String
  type CredDefId = String
}


/**
 * a base sdk class for issuer/holder sdk
 * @param param sdk parameters
 */
abstract class SdkBase(param: SdkParam) {

  def fetchAgencyKey(): AgencyPublicDid = {
    val resp = sendGET("agency")
    val json = parseHttpResponse(resp)
    val apd = JacksonMsgCodec.fromJson[AgencyPublicDid](json)
    require(apd.DID.nonEmpty, "agency DID should not be empty")
    require(apd.verKey.nonEmpty, "agency verKey should not be empty")
    storeTheirKey(apd.didPair)
    agencyPublicDidOpt = Option(apd)
    apd
  }

  protected def provisionVerityAgentBase(createAgentMsg: Any): AgentCreated = {
    val createAgentJsonMsg = withNewThreadIdAdded(createJsonString(createAgentMsg, AgentProvisioningMsgFamily))
    val packedMsg = packFromLocalAgentKey(createAgentJsonMsg, Set(KeyParam.fromVerKey(agencyVerKey)))
    val routedPackedMsg = prepareFwdMsg(agencyDID, agencyDID, packedMsg)
    val unpackedMsg = parseAndUnpackResponse[AgentCreated](sendPOST(routedPackedMsg))
    val verityAgent = unpackedMsg.msg
    verityAgentDidPairOpt = Option(DidPair(verityAgent.selfDID, verityAgent.agentVerKey))
    storeTheirKey(DidPair(verityAgent.selfDID, verityAgent.agentVerKey))
    verityAgent
  }

  protected def packForMyVerityAgent(msg: String): Array[Byte] = {
    val packedMsgForVerityAgent = packFromLocalAgentKey(msg, Set(KeyParam.fromVerKey(verityAgentDidPair.verKey)))
    prepareFwdMsg(agencyDID, verityAgentDidPair.DID, packedMsgForVerityAgent)
  }

  protected def packFromLocalAgentKey(msg: String, recipVerKeyParams: Set[KeyParam]): Array[Byte] = {
    packMsg(msg, recipVerKeyParams, Option(KeyParam.fromVerKey(myLocalAgentVerKey)))
  }

  /**
   *
   * @param recipDID recipient of fwd msg
   * @param fwdToDID destination of the given 'msg'
   * @param msg the message to be sent
   * @return
   */
  protected def prepareFwdMsg(recipDID: DID, fwdToDID: DID, msg: Array[Byte]): Array[Byte] = {
    val fwdJson = AgentMsgPackagingUtil.buildFwdJsonMsg(MPF_INDY_PACK, fwdToDID, msg)
    val senderKey = if (recipDID == agencyDID) None else Option(KeyParam.fromVerKey(myLocalAgentVerKey))
    packMsg(fwdJson, Set(KeyParam.fromDID(recipDID)), senderKey)
  }

  protected def packMsg(msg: String,
                      recipVerKeyParams: Set[KeyParam],
                      senderKeyParam: Option[KeyParam]): Array[Byte] = {
    val msgBytes = msg.getBytes()
    val pm = testWalletAPI.executeSync[PackedMsg](
      PackMsg(msgBytes, recipVerKeyParams, senderKeyParam))
    pm.msg
  }

  def unpackMsg[T: ClassTag](msg: Array[Byte]): ReceivedMsgParam[T] = {
    val json = testWalletAPI.executeSync[UnpackedMsg](UnpackMsg(msg)).msgString
    val jsonObject = new JSONObject(json)
    ReceivedMsgParam(jsonObject.getString("message"))
  }

  protected def createJsonString(msg: Any, msgFamily: MsgFamily): String = {
    val msgType = msgFamily.msgType(msg.getClass)
    val typeStr = MsgFamily.typeStrFromMsgType(msgType)
    createJsonString(typeStr, msg)
  }

  protected def withNewThreadIdAdded(msg: String): String = {
    withThreadIdAdded(msg, UUID.randomUUID().toString)
  }

  protected def withThreadIdAdded(msg: String, threadId: ThreadId): String = {
    val coreJson = new JSONObject(msg)
    val threadJSON = new JSONObject()
    threadJSON.put("thid", threadId)
    coreJson.put("~thread", threadJSON).toString
  }

  protected def createJsonString(typeStr: String, msg: Any): String = {
    val coreJson = createJSONObject(msg)
    coreJson.put("@type", typeStr).toString
  }

  protected def createJSONObject(msg: Any): JSONObject = {
    val coreJson = DefaultMsgCodec.toJson(msg)
    new JSONObject(coreJson)
  }

//  protected def legacyType(name: String, ver: String): JSONObject = {
//    val jsonObj = new JSONObject()
//    jsonObj.put("name", name)
//    jsonObj.put("ver", ver)
//    jsonObj
//  }

  protected def getMsgFamilyOpt[T: ClassTag]: Option[MsgFamily] = {
    val clazz = implicitly[ClassTag[T]].runtimeClass
    getMsgFamilyOpt(clazz)
  }

  protected def getMsgFamily[T: ClassTag]: MsgFamily = {
    val clazz = implicitly[ClassTag[T]].runtimeClass
    getMsgFamilyOpt.getOrElse(
      throw new RuntimeException("message family not found for given message: " + clazz.getSimpleName)
    )
  }

  protected def getMsgFamily(msg: Any): MsgFamily = {
    getMsgFamilyOpt(msg.getClass).getOrElse(
      throw new RuntimeException("message family not found for given message: " + msg.getClass.getSimpleName)
    )
  }

  protected def getMsgFamilyOpt(clazz: Class[_]): Option[MsgFamily] = {
    val protoDefOpt =
      protoDefs
        .find { pd =>
          Try (pd.msgFamily.lookupAllMsgName(clazz).nonEmpty).getOrElse(false)
        }
    protoDefOpt.map(_.msgFamily)
  }

  protected def checkOKResponse(resp: HttpResponse): HttpResponse = {
    checkResponse(resp, OK)
  }

  protected def checkResponse(resp: HttpResponse, expected: StatusCode): HttpResponse = {
    require(resp.status.intValue() == expected.intValue,
      s"http response was not ${expected.value}: ${resp.status}")
    resp
  }

  protected def sendPOST(payload: Array[Byte]): HttpResponse =
    sendBinaryReqToUrl(payload, param.verityPackedMsgUrl)

  protected def sendBinaryReqToUrl(payload: Array[Byte], url: String): HttpResponse = {
    awaitFut(
      Http().singleRequest(
        HttpRequest(
          method=HttpMethods.POST,
          uri = url,
          entity = HttpEntity(
            ContentTypes.`application/octet-stream`,
            payload
          )
        )
      )
    )
  }

  protected def sendGET(pathSuffix: String): HttpResponse = {
    val actualPath = param.myVerityBaseUrl + s"/$pathSuffix"
    awaitFut(
      Http().singleRequest(
        HttpRequest(
          method=HttpMethods.GET,
          uri = actualPath,
          entity = HttpEntity.Empty
        )
      )
    )
  }

  protected def parseAndUnpackResponse[T: ClassTag](resp: HttpResponse): ReceivedMsgParam[T] = {
    val packedMsg = parseHttpResponse(resp)
    unpackMsg[T](packedMsg.getBytes)
  }

  protected def parseHttpResponse(resp: HttpResponse): String = {
    awaitFut(resp.entity.dataBytes.runReduce(_ ++ _).map(_.utf8String))
  }

  protected def awaitFut[T](fut: Future[T]): T = {
    Await.result(fut, Duration(20, SECONDS))
  }

  def randomUUID(): String = UUID.randomUUID().toString
  def randomSeed(): String = randomUUID().replace("-", "")

  val protoDefs = protocols.protocolRegistry.entries.map(_.protoDef).toList

  type ConnId = String

  implicit val walletAPIParam: WalletAPIParam = WalletAPIParam(UUID.randomUUID().toString)
  implicit val system: ActorSystem = ActorSystem(randomUUID())

  var agencyPublicDidOpt: Option[AgencyPublicDid] = None

  //local agent (on sdk side)
  val localAgentDidPair: DidPair = createNewKey()

  //verity agent (edge/cloud)
  var verityAgentDidPairOpt: Option[DidPair] = None

  var myPairwiseRelationships: Map[ConnId, PairwiseRel] = Map.empty

  def createNewKey(seed: Option[String] = Option(randomSeed())): DidPair = {
    testWalletAPI.executeSync[NewKeyCreated](CreateNewKey(seed = seed)).didPair
  }

  def storeTheirKey(didPair: DidPair): Unit = {
    testWalletAPI.executeSync[TheirKeyStored](StoreTheirKey(didPair.DID, didPair.verKey))
  }

  def agencyPublicDid: AgencyPublicDid = agencyPublicDidOpt.getOrElse(
    throw new RuntimeException("agency key is not yet fetched")
  )
  def verityAgentDidPair: DidPair = verityAgentDidPairOpt.getOrElse(
    throw new RuntimeException("verity agent not yet created")
  )
  def agencyDID: DID = agencyPublicDid.DID
  def agencyVerKey: VerKey = agencyPublicDid.verKey
  def myLocalAgentVerKey: VerKey = localAgentDidPair.verKey

  protected lazy val testWalletAPI: LegacyWalletAPI = {
    val walletProvider = LibIndyWalletProvider
    val walletAPI = new LegacyWalletAPI(new TestAppConfig(), walletProvider, None)
    walletAPI.executeSync[WalletCreated.type](CreateWallet())
    walletAPI
  }
}

case class PairwiseRel(myLocalAgentDIDPair: Option[DidPair] = None,
                       verityAgentDIDPair: Option[DidPair] = None,
                       theirDIDDoc: Option[DIDDoc] = None) {

  def myLocalAgentDIDPairReq: DidPair = myLocalAgentDIDPair.getOrElse(throw new RuntimeException("my pairwise key not exists"))
  def myPairwiseDID: DID = myLocalAgentDIDPairReq.DID
  def myPairwiseVerKey: VerKey = myLocalAgentDIDPairReq.verKey

  def myVerityAgentDIDPairReq: DidPair = verityAgentDIDPair.getOrElse(throw new RuntimeException("verity agent key not exists"))
  def myVerityAgentDID: DID = myVerityAgentDIDPairReq.DID
  def myVerityAgentVerKey: VerKey = myVerityAgentDIDPairReq.verKey

  def theirDIDDocReq: DIDDoc = theirDIDDoc.getOrElse(throw new RuntimeException("their DIDDoc not exists"))
  def theirAgentVerKey: VerKey = theirDIDDocReq.verkey
  def theirRoutingKeys: Vector[VerKey] = theirDIDDocReq.routingKeys
  def theirServiceEndpoint: ServiceEndpoint = theirDIDDocReq.endpoint

  def withProvisionalTheirDidDoc(invitation: Invitation): PairwiseRel = {
    val ciValue = invitation.ciValueDecoded.getOrElse(throw new RuntimeException("invalid url: " + invitation.inviteURL))
    val ciJson = new JSONObject(ciValue)
    val theirVerKey = ciJson.getJSONArray("recipientKeys").toList.asScala.head.toString
    val theirRoutingKeys = ciJson.getJSONArray("routingKeys").toList.asScala.map(_.toString).toVector
    val theirServiceEndpoint = ciJson.getString("serviceEndpoint")
    val didDoc = DIDDoc(
      theirVerKey,
      theirVerKey,
      theirServiceEndpoint,
      theirRoutingKeys
    )
    copy(theirDIDDoc = Option(didDoc))
  }

  def withFinalTheirDidDoc(connResp: ConnResponse): PairwiseRel = {
    val conn_bytes = Base64Util.getBase64UrlDecoded(connResp.`connection~sig`.sig_data)
    val connJson = new String(conn_bytes.drop(8), StandardCharsets.UTF_8)
    val conn = DefaultMsgCodec.fromJson[Msg.Connection](connJson)
    val toDidDoc = conn.did_doc.toDIDDoc
    copy(theirDIDDoc = Option(toDidDoc))
  }
}

object ReceivedMsgParam {

  def apply[T: ClassTag](msg: String): ReceivedMsgParam[T] = {
    val message = new JSONObject(msg)
    val threadId = Try {
      val thread = message.getJSONObject("~thread")
      Option(thread.getString("thid"))
    }.getOrElse(None)
    val expMsg = DefaultMsgCodec.fromJson[T](message.toString)
    ReceivedMsgParam(expMsg, msg, None, threadId)
  }
}

/**
 *
 * @param msg the received message
 * @param msgIdOpt message id used by verity agent to uniquely identify a message
 *                 (this will be only available for messages retrieved from CAS/EAS)
 * @param threadIdOpt received message's thread id
 * @tparam T
 */
case class ReceivedMsgParam[T: ClassTag](msg: T,
                                         jsonMsgStr: String,
                                         msgIdOpt: Option[MsgId] = None,
                                         threadIdOpt: Option[ThreadId]=None) {
  def msgId: MsgId = msgIdOpt.getOrElse(throw new RuntimeException("msgId not available in received message"))
}


case class SdkParam(myVerityBaseUrl: String) {
  def verityPackedMsgUrl: String = s"$myVerityBaseUrl/agency/msg"
  def verityRestApiUrl: String = s"$myVerityBaseUrl/api"
}