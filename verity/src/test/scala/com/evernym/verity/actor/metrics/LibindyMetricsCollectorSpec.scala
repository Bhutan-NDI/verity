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
  lazy val libindyMetricsCollector: ActorRef = platform.libindyMetricsCollector
  lazy val clientIpAddress: String = "127.0.0.1"
  lazy val reqId: ReqId = UUID.randomUUID().toString

  "LibindyMetricsCollector" - {

    "collected metrics from Libindy" - {
      "should be sent to Kamon" in {
        libindyMetricsCollector ! CollectLibindyMetrics()
        expectMsgType[CollectLibindySuccess]
        val criteria = MetricsFilterCriteria(filtered = false)
        awaitCond(MetricsReader.getNodeMetrics(criteria).metrics.exists(
          metricDetail => metricDetail.name.contains("libindy_threadpool_active_count")), 60.seconds)
      }
    }
  }
}