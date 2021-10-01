package com.evernym.verity.vdr

import akka.testkit.TestKitBase
import akka.actor.typed.scaladsl.adapter._
import com.evernym.verity.actor.testkit.HasBasicActorSystem
import com.evernym.verity.testkit.BasicSpec
import com.evernym.verity.util2.ExecutionContextProvider
import com.evernym.verity.vdr.service.{IndyLedger, Ledger, VDRToolsConfig}
import org.scalatest.concurrent.Eventually

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._


class VDRActorAdapterSpec
  extends TestKitBase
    with HasBasicActorSystem
    with BasicSpec
    with Eventually {


  "VDRActorAdapter" - {

    "when created with invalid configuration" - {
      "should throw an error" in {
        val ex = intercept[RuntimeException] {
          createVDRActorAdapter(
            List(
              defaultIndyLedger,
              anotherIndyLedger
            )
          )
        }
        ex.getMessage shouldBe "[VDR] ledgers can not have shared namespaces"
      }
    }

    "when created with valid configuration" - {
      "should be successful" in {
        createVDRActorAdapter(List(defaultIndyLedger))
      }
    }

    "when asked to prepare schema txn with non fqdid" - {
      "should result in failure" in {
        val vdrAdapter = createVDRActorAdapter(List(defaultIndyLedger))
        val ex = intercept[RuntimeException] {
          Await.result(
            vdrAdapter.prepareSchemaTxn(
              "schemaJson",
              "did:indy:sovrin:F72i3Y3Q4i466efjYJYCHM:2:degree:1.1.1",
              "did1",
              None
            ),
            apiTimeout
          )
        }
        ex.getMessage shouldBe "invalid fq did: did1"
      }
    }

    "when asked to prepare schema txn with non fqSchemaId" - {
      "should result in failure" in {
        val vdrAdapter = createVDRActorAdapter(List(defaultIndyLedger))
        val ex = intercept[RuntimeException] {
          Await.result(
            vdrAdapter.prepareSchemaTxn(
              "schemaJson",
              "F72i3Y3Q4i466efjYJYCHM:2:degree:1.1.1",
              "did:indy:sovrin:F72i3Y3Q4i466efjYJYCHM",
              None
            ),
            apiTimeout
          )
        }
        ex.getMessage shouldBe "invalid identifier: F72i3Y3Q4i466efjYJYCHM:2:degree:1.1.1"
      }
    }

    "when asked to prepare schema txn with valid data" - {
      "should result in failure" in {
        val vdrAdapter = createVDRActorAdapter(List(defaultIndyLedger))
        Await.result(
          vdrAdapter.prepareSchemaTxn(
            "schemaJson",
            "did:indy:sovrin:F72i3Y3Q4i466efjYJYCHM:2:degree:1.1.1",
            "did:indy:sovrin:F72i3Y3Q4i466efjYJYCHM",
            None
          ),
          apiTimeout
        )
      }
    }

    "when asked to submit schema txn with valid data" - {
      "should result in failure" in {
        val vdrAdapter = createVDRActorAdapter(List(defaultIndyLedger))
        val preparedTxn = Await.result(
          vdrAdapter.prepareSchemaTxn(
            """{"field1":"value"1}""",
            "did:indy:sovrin:F72i3Y3Q4i466efjYJYCHM:2:degree:1.1.1",
            "did:indy:sovrin:F72i3Y3Q4i466efjYJYCHM",
            None
          ),
          apiTimeout
        )
        Await.result(
          vdrAdapter.submitTxn(
            preparedTxn,
            "signature".getBytes,
            Array.empty
          ),
          apiTimeout
        )
      }
    }

    "when asked to resolve schema for invalid schema id" - {
      "it should fail" in {
        val vdrAdapter = createVDRActorAdapter(List(defaultIndyLedger))
        val ex = intercept[RuntimeException] {
          Await.result(
            vdrAdapter.resolveSchema("did1"),
            apiTimeout
          )
        }
        ex.getMessage shouldBe "invalid fq did: did1"
      }
    }

    "when asked to resolve schema for schema id" - {
      "it should fail" in {
        val vdrAdapter = createVDRActorAdapter(List(defaultIndyLedger))
        val preparedTxn = Await.result(
          vdrAdapter.prepareSchemaTxn(
            """{"field1":"value"1}""",
            "did:indy:sovrin:F72i3Y3Q4i466efjYJYCHM:2:degree:1.1.1",
            "did:indy:sovrin:F72i3Y3Q4i466efjYJYCHM",
            None
          ),
          apiTimeout
        )
        Await.result(
          vdrAdapter.submitTxn(
            preparedTxn,
            "signature".getBytes,
            Array.empty
          ),
          apiTimeout
        )

        val schema = Await.result(
          vdrAdapter.resolveSchema("did:indy:sovrin:F72i3Y3Q4i466efjYJYCHM:2:degree:1.1.1"),
          apiTimeout
        )
        schema.fqId shouldBe "did:indy:sovrin:F72i3Y3Q4i466efjYJYCHM:2:degree:1.1.1"
        schema.json shouldBe """{"field1":"value"1}"""
      }
    }
  }

  def createVDRActorAdapter(ledgers: List[Ledger]): VDRActorAdapter = {
    testVDRTools = new TestVDRTools
    val testVDRToolsFactory = { _: VDRToolsFactoryParam => testVDRTools }
    val vdrToolsConfig = VDRToolsConfig("/usr/lib", ledgers)
    new VDRActorAdapter(testVDRToolsFactory, vdrToolsConfig, None)(ec, system.toTyped)
  }

  lazy val apiTimeout: FiniteDuration = 5.seconds

  lazy val defaultIndyLedger: IndyLedger = IndyLedger(List("indy:sovrin", "sov"), "genesis1-path", None)
  lazy val anotherIndyLedger: IndyLedger = IndyLedger(List("indy:sovrin", "sov"), "genesis2-path", None)

  implicit lazy val ecp: ExecutionContextProvider = new ExecutionContextProvider(appConfig)
  implicit val ec: ExecutionContext = ecp.futureExecutionContext

  var testVDRTools = new TestVDRTools
}
