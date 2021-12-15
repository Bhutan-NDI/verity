package com.evernym.verity.itemmanager

import akka.actor.typed.scaladsl.adapter._
import com.evernym.verity.actor.itemmanager.ItemManagerEntityHelper
import com.evernym.verity.actor.testkit.ActorSpec
import com.evernym.verity.testkit.BasicSpec
import com.evernym.verity.util2.ExecutionContextProvider

import scala.concurrent.ExecutionContext

class ItemManagerEntityHelperSpec
  extends ActorSpec
    with BasicSpec {

  val imh = new ItemManagerEntityHelper("123", "Outbox", system.toTyped)

  "ItemManagerEntityHelper" - {

    "when tried to register an entity first time" - {
      "should be successful" in {
        imh.register()
        imh.isAlreadyRegistered shouldBe true
        imh.isAlreadyUnregistered shouldBe false
      }
    }

    "when tried to unregister it" - {
      "should be successful" in {
        imh.deregister()
        imh.isAlreadyUnregistered shouldBe true
        imh.isAlreadyRegistered shouldBe false
      }
    }

    "when tried to register an entity again" - {
      "should be successful" in {
        imh.register()
        imh.isAlreadyRegistered shouldBe true
      }
    }
  }

  lazy val ecp: ExecutionContextProvider = new ExecutionContextProvider(appConfig)
  implicit lazy val executionContext: ExecutionContext = ecp.futureExecutionContext
  override def executionContextProvider: ExecutionContextProvider = ecp
}
