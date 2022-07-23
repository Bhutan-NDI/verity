package com.evernym.verity.cache.fetchers

import com.evernym.verity.util2.Status.StatusDetailException
import com.evernym.verity.cache.LEDGER_GET_VER_KEY_CACHE_FETCHER
import com.evernym.verity.cache.base.{FetcherParam, KeyDetail, KeyMapping}
import com.evernym.verity.config.AppConfig
import com.evernym.verity.config.ConfigConstants._
import com.evernym.verity.ledger.Submitter
import com.evernym.verity.did.DidStr
import com.evernym.verity.vdr.{VDRAdapter, VDRUtil}

import scala.concurrent.{ExecutionContext, Future}

class LedgerVerKeyCacheFetcher(val vdr: VDRAdapter,
                               val appConfig: AppConfig, executionContext: ExecutionContext)
  extends AsyncCacheValueFetcher {
  override implicit def futureExecutionContext: ExecutionContext = executionContext

  lazy val vdrMultiLedgerSupportEnabled: Boolean = appConfig.getBooleanReq(VDR_MULTI_LEDGER_SUPPORT_ENABLED)
  lazy val vdrUnqualifiedLedgerPrefix: String = appConfig.getStringReq(VDR_UNQUALIFIED_LEDGER_PREFIX)
  lazy val vdrLedgerPrefixMappings: Map[String, String] = appConfig.getMap(VDR_LEDGER_PREFIX_MAPPINGS)

  lazy val fetcherParam: FetcherParam = LEDGER_GET_VER_KEY_CACHE_FETCHER
  lazy val cacheConfigPath: Option[String] = Option(ROUTING_DETAIL_CACHE)

  //time to live in seconds, afterwards they will be considered as expired and re-fetched from source
  override lazy val defaultExpiryTimeInSeconds: Option[Int] = Option(1800)

  override def toKeyDetailMappings(keyDetails: Set[KeyDetail]): Set[KeyMapping] = {
    keyDetails.map { kd =>
      val gvp = kd.keyAs[GetVerKeyParam]
      KeyMapping(kd, gvp.did, gvp.did)
    }
  }

  override def getByKeyDetail(kd: KeyDetail): Future[Map[String, AnyRef]] = {
    val gvkp = kd.keyAs[GetVerKeyParam]
    val didDocFut = vdr.resolveDID(VDRUtil.toFqDID(gvkp.did, vdrMultiLedgerSupportEnabled, vdrUnqualifiedLedgerPrefix, vdrLedgerPrefixMappings))
    didDocFut
      .map { dd =>Map(gvkp.did -> dd.verKey)}
      .recover {
        case StatusDetailException(sd) => throw buildUnexpectedResponse(sd)
      }
  }
}

case class GetVerKeyParam(did: DidStr, submitterDetail: Submitter) {
  override def toString: String = s"DID: $did, SubmitterDetail: $Submitter"
}
