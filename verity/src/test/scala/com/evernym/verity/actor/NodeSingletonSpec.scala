package com.evernym.verity.actor

import akka.actor.ActorRef
import com.evernym.verity.actor.node_singleton.DrainInitiated
import com.evernym.verity.actor.testkit.PersistentActorSpec
import com.evernym.verity.actor.testkit.checks.UNSAFE_IgnoreLog
import com.evernym.verity.testkit.BasicSpecWithIndyCleanup


class NodeSingletonSpec 
  extends PersistentActorSpec
  with BasicSpecWithIndyCleanup {

  lazy val nodeSingleton: ActorRef = platform.nodeSingleton

  configNodeSpecs()

  def configNodeSpecs(): Unit = {

    "ConfigNodeSpec" - {
      "should be able to reload config" taggedAs UNSAFE_IgnoreLog in {
        nodeSingleton ! RefreshNodeConfig

        expectMsgPF() {
          case NodeConfigRefreshed =>
        }
      }
    }
  }

  def drainNodeSpecs(): Unit = {

    "DrainNodeSpec" - {
      "should be able to initiate draining phase of shutdown" in {
        nodeSingleton ! DrainInitiated

        expectMsg(NodeConfigRefreshed)
      }
    }
  }

}
