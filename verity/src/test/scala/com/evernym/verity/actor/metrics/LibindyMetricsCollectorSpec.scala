package com.evernym.verity.actor.metrics

import java.util.UUID

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKitBase}
import com.evernym.verity.ReqId
import com.evernym.verity.actor.MetricsFilterCriteria
import com.evernym.verity.actor.testkit.AkkaTestBasic
import com.evernym.verity.actor.testkit.actor.ProvidesMockPlatform
import com.evernym.verity.metrics.MetricsReader
import com.evernym.verity.testkit.BasicSpec
import org.scalatest.concurrent.Eventually
import com.evernym.verity.ExecutionContextProvider.futureExecutionContext

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}

class LibindyMetricsCollectorSpec
  extends TestKitBase
    with ProvidesMockPlatform
    with BasicSpec
    with ImplicitSender
    with Eventually {

  implicit lazy val system: ActorSystem = AkkaTestBasic.system()
  lazy val libindyMetricsTracker: ActorRef = platform.libindyMetricsTracker
  lazy val clientIpAddress: String = "127.0.0.1"
  lazy val reqId: ReqId = UUID.randomUUID().toString

  "LibindyMetricsTracker" - {

    "collected metrics from Libindy" - {
      "should be sent to Kamon" in {
//        libindyMetricsTracker ! CollectLibindyMetrics()
//        expectMsgType[CollectLibindySuccess]
        val criteria = MetricsFilterCriteria(filtered = false)
        awaitCond(MetricsReader.getNodeMetrics(criteria).metrics.exists(metricDetail => metricDetail.name.contains("threadpool_active_count")), 100.seconds)
      }
    }
  }
}
