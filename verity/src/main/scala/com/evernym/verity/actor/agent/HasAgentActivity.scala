package com.evernym.verity.actor.agent

import com.evernym.verity.actor.{ForIdentifier, ShardRegionCommon}
import com.evernym.verity.actor.metrics.{ActivityTracking, ActivityWindow, AgentActivity}
import com.evernym.verity.metrics.CustomMetrics.AS_NEW_USER_AGENT_COUNT
import com.evernym.verity.metrics.MetricsWriter
import com.evernym.verity.protocol.engine.DomainId
import com.evernym.verity.util.TimeUtil
import com.evernym.verity.util.OptionUtil.optionToEmptyStr

trait HasAgentActivity extends ShardRegionCommon {
  object AgentActivityTracker {

    private def sendToRegion(id: DomainId, msg: ActivityTracking): Unit =
      activityTrackerRegion ! ForIdentifier(id, msg)

    def track(msgType: String,
              domainId: DomainId,
              relId: Option[String]=None,
              timestamp: String=TimeUtil.nowDateString) : Unit = {
          sendToRegion(
            domainId,
            AgentActivity(domainId, timestamp, msgType, relId)
          )
    }

    def newAgent(sponsorId: Option[String]=None): Unit =
      MetricsWriter.gaugeApi.incrementWithTags(AS_NEW_USER_AGENT_COUNT, Map("sponsorId" -> sponsorId))

    def setWindows(domainId: DomainId, windows: ActivityWindow): Unit =
      sendToRegion(domainId, windows)
  }

}
