package com.evernym.verity.vdr.service

import com.evernym.verity.did.DidStr
import com.evernym.verity.vdr.{FQSchemaId, Namespace}

import scala.concurrent.Future

//A thin wrapper around VDRTools API for production code
class VDRToolsImpl(libDirLocation: String)
  extends VDRTools {

  private val wrapperVDR: Any = null //replace `null` with actual VDR object creation call

  override def registerIndyLedger(namespaces: List[Namespace],
                                  genesisTxnFilePath: String,
                                  taaConfig: Option[TAAConfig]): Future[VDR_LedgerRegistered] = {

    //TODO: replace this mock implementation with actual VDR wrapper apis calls once it is available
    Future.successful(VDR_LedgerRegistered())
  }

  override def prepareSchemaTxn(schemaJson: String,
                                fqSchemaId: FQSchemaId,
                                submitterDid: DidStr,
                                endorser: Option[String]): Future[VDR_PreparedTxn] = {
    //TODO: replace this mock implementation with actual VDR wrapper apis calls once it is available
    Future.successful(VDR_PreparedTxn("", VDR_NoSignature, Array.empty, VDR_NoEndorsement))
  }

  override def submitTxn(preparedTxn: VDR_PreparedTxn,
                         signature: Array[Byte],
                         endorsement: Array[Byte]): Future[VDR_SubmittedTxn] = {
    //TODO: replace this mock implementation with actual VDR wrapper apis calls once it is available
    Future.successful(VDR_SubmittedTxn())
  }
}