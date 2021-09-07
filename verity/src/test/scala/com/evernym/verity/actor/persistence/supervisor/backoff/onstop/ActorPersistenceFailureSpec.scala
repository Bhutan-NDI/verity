package com.evernym.verity.actor.persistence.supervisor.backoff.onstop

import akka.testkit.EventFilter
import com.evernym.verity.util2.ExecutionContextProvider
import com.evernym.verity.actor.persistence.supervisor.{GeneratePersistenceFailure, MockActorPersistenceFailure}
import com.evernym.verity.actor.testkit.{ActorSpec, AkkaTestBasic}
import com.evernym.verity.testkit.BasicSpec
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.Eventually

//This test will test the `Stop` strategy: https://github.com/akka/akka/blob/622d8af0ef9f685ee1e91b04177926ca938376ac/akka-actor/src/main/scala/akka/actor/FaultHandling.scala#L208
// (shouldn't change anything as it is not changing any behavior for 'Stop' strategy)

class ActorPersistenceFailureSpec
  extends ActorSpec
    with BasicSpec
    with Eventually {

  lazy val mockUnsupervised = system.actorOf(MockActorPersistenceFailure.backOffOnStopProps(appConfig, ecp.futureExecutionContext))

  override def expectDeadLetters: Boolean = true


  "OnStop BackoffSupervised actor" - {

    "when throws an exception during persistence" - {
      "should restart actor once" in {
        EventFilter.error(pattern = "purposefully throwing exception", occurrences = 1) intercept {
          mockUnsupervised ! GeneratePersistenceFailure
          expectNoMessage()
        }
        //TODO: how to test that the actor is restarted?
        // found some unexplained  behaviour for
        // handling persistence failure (the default strategy seems to be Restart)
        // but it doesn't seem to enter into 'preRestart' method in 'CoreActor'
      }
    }
  }

  override def overrideConfig: Option[Config] = Option {
    ConfigFactory.parseString (
      s"""
        akka.test.filter-leeway = 15s   # to make the event filter run for little longer time
        verity.persistent-actor.base.supervisor {
          enabled = true
          strategy = OnStop
          min-seconds = 3
          max-seconds = 20
          random-factor = 0
        }
      """
    ).withFallback(
      AkkaTestBasic.customJournal("com.evernym.verity.actor.persistence.supervisor.GeneratePersistenceFailureJournal")
    )
  }

  lazy val ecp: ExecutionContextProvider = new ExecutionContextProvider(appConfig)
  override def executionContextProvider: ExecutionContextProvider = ecp
}

