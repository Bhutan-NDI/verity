package com.evernym.verity.actor.agent.agency

import com.evernym.verity.constants.Constants.{AGENCY_DID_KEY, KEY_VALUE_MAPPER_ACTOR_CACHE_FETCHER_ID}

import scala.concurrent.Future
import com.evernym.verity.ExecutionContextProvider.futureExecutionContext
import com.evernym.verity.cache.base.{Cache, CacheQueryResponse, GetCachedObjectParam, KeyDetail}

trait AgencyIdUtil {

  def getAgencyDID(implicit generalCache: Cache): Future[String] = {
    val gcop = GetCachedObjectParam(Set(KeyDetail(AGENCY_DID_KEY, required = false)), KEY_VALUE_MAPPER_ACTOR_CACHE_FETCHER_ID)
    generalCache.getByParamAsync(gcop).mapTo[CacheQueryResponse].map { cqr =>
      cqr.getAgencyDIDReq
    }
  }
}
