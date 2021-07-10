package com.evernym.verity.actor.typed

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.persistence.testkit.PersistenceTestKitSnapshotPlugin
import akka.persistence.testkit.scaladsl.{EventSourcedBehaviorTestKit, PersistenceTestKit, SnapshotTestKit}
import com.typesafe.config.{Config, ConfigFactory}


abstract class BehaviourSpecBase
  extends ScalaTestWithActorTestKit(
    ActorTestKit(
      "TestSystem",
      TypedTestKit.config.withFallback(TypedTestKit.clusterConfig)
    )
  )

abstract class EventSourcedBehaviourSpecBase
  extends BehaviourSpecBase {
  lazy val persTestKit: PersistenceTestKit = PersistenceTestKit(system)
  lazy val snapTestKit: SnapshotTestKit = SnapshotTestKit(system)
}

object TypedTestKit {

  val config: Config = ConfigFactory.empty
    .withFallback(EventSourcedBehaviorTestKit.config)
    .withFallback(PersistenceTestKitSnapshotPlugin.config)

  val clusterConfig: Config = ConfigFactory.parseString(
    """
    akka {
      actor {
        provider = "cluster"
      }
      remote.artery {
        canonical {
          hostname = "127.0.0.1"
          port = 2551
        }
      }

      cluster {
        seed-nodes = [
          "akka://TestSystem@127.0.0.1:2551",
        ]
        downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
      }
    }
    """)
}
