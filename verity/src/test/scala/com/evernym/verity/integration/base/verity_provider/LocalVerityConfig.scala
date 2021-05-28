package com.evernym.verity.integration.base.verity_provider

import akka.persistence.journal.leveldb.SharedLeveldbJournal
import com.evernym.verity.config.CommonConfig.{LIB_INDY_LEDGER_POOL_NAME, LIB_INDY_LIBRARY_DIR_LOCATION, LIB_INDY_WALLET_TYPE}
import com.evernym.verity.integration.base.SharedEventStore
import com.typesafe.config.{Config, ConfigFactory}

import java.net.InetAddress
import java.nio.file.Path
import java.util.UUID
import scala.util.Random


object PortProfile {
  def random(): PortProfile = {
    val random = new Random(UUID.randomUUID().toString.hashCode)
    val httpPort = 9000 + random.nextInt(900) + random.nextInt(90) + random.nextInt(9)
    val arteryPort = 2000 + random.nextInt(900)  + random.nextInt(90) + random.nextInt(9)
    val akkaMgmtPort = 8000 + random.nextInt(900)  + random.nextInt(90) + random.nextInt(9)
    PortProfile(httpPort, arteryPort, akkaMgmtPort)
  }
}
case class PortProfile(http: Int, artery: Int, akka_management: Int)

object LocalVerityConfig {

  val defaultPorts: PortProfile = PortProfile(9002, 2552, 8552)

  private def messageSerialization: Config = {
    ConfigFactory.parseString(
      """akka.actor.serialize-messages = on
        |akka.actor.allow-java-serialization = off
        |""".stripMargin
    )
  }

  /**
   * @param ports port profile of the this/current node of the cluster
   * @param otherNodeArteryPorts other node's (of the same cluster) artery port
   *                             to be used to populate seed-nodes
   * @return
   */
  private def useCustomPort(ports: PortProfile, otherNodeArteryPorts: List[Int]): Config = {
    ConfigFactory.parseString(
      s"""
         |verity.http.port = ${ports.http}
         |verity.endpoint.port = ${ports.http}
         |akka.remote.artery.canonical.hostname = ${InetAddress.getLocalHost.getHostAddress}
         |akka.remote.artery.canonical.port = ${ports.artery}
         |akka.management.http.port = ${ports.akka_management}
      """.stripMargin +
        "akka.cluster.seed-nodes = [" + "\n" +
          (ports.artery +: otherNodeArteryPorts).sorted.map { port =>
            s"""  \"akka://verity@${InetAddress.getLocalHost.getHostAddress}:$port\""""
          }.mkString(",\n") + "\n" +
        "]".stripMargin
    )
  }

  //Use local/shared leveldb persistence
  private def useLevelDBPersistence(tempDir: Path, sharedEventStore: Option[SharedEventStore]=None): Config = {
    sharedEventStore match {
      case Some(ses) =>
        ConfigFactory.parseString(s"""
         |akka.actor.allow-java-serialization = on
         |akka.extensions = ["akka.persistence.Persistence"]
         |akka.persistence.journal.auto-start-journals = [""]
         |akka.persistence.journal.proxy.target-journal-address = "${ses.address}"
         |akka.persistence.snapshot-store.proxy.target-snapshot-store-address = "${ses.address}"
         |akka.persistence.journal {
         |  plugin = "akka.persistence.journal.proxy"
         |  proxy.target-journal-plugin = "akka.persistence.journal.leveldb"
         |}
         |akka.persistence.snapshot-store {
         |  plugin = "akka.persistence.snapshot-store.proxy"
         |  proxy.target-snapshot-store-plugin = "akka.persistence.snapshot-store.local"
         |}""".stripMargin).withFallback(SharedLeveldbJournal.configToEnableJavaSerializationForTest)
      case None      =>
        ConfigFactory.parseString(s"""
         |akka.persistence.journal {
         |  plugin = "akka.persistence.journal.leveldb"
         |  leveldb {
         |    dir = "$tempDir/journal"
         |    native = false
         |  }
         |}
         |akka.persistence.snapshot-store {
         |  plugin = "akka.persistence.snapshot-store.local"
         |  local = {
         |    dir = "${tempDir.resolve("snapshots")}"
         |  }
         |}
         |""".stripMargin
        )
    }
  }

  private def useDefaultWallet(tempDir: Path): Config = {
    ConfigFactory.parseString(
      s"""
         |$LIB_INDY_WALLET_TYPE = "default"
         |$LIB_INDY_LIBRARY_DIR_LOCATION  = "${tempDir.resolve("indy")}"
         |""".stripMargin
    )
  }

  private def changePoolName(): Config = {
    ConfigFactory.parseString(
      s"""
         |$LIB_INDY_LEDGER_POOL_NAME = "verity-pool"
         |""".stripMargin
    )
  }

  private def akkaConfig() = {
    ConfigFactory.parseString(
      s"""
         |akka.actor.provider = cluster
         |akka.http.server.remote-address-header = on
         |akka.cluster.jmx.multi-mbeans-in-same-jvm = on
         |""".stripMargin
    )
  }

  private def configureLibIndy(taaEnabled: Boolean, taaAutoAccept: Boolean): Config = {
    ConfigFactory.parseString(
      s"""
         |verity.lib-indy {
         |  ledger {
         |    transaction_author_agreement = {
         |      agreements = {
         |        "1.0.0" {
         |          "digest" = "a0ab0aada7582d4d211bf9355f36482e5cb33eeb46502a71e6cc7fea57bb8305"
         |          "mechanism" = "on_file"
         |          "time-of-acceptance" = "2019-11-18"
         |        }
         |      }
         |      enabled = $taaEnabled
         |      auto-accept = $taaAutoAccept
         |    }
         |  }
         |}""".stripMargin
    )
  }

  private def turnOffWarnings(): Config = {
    ConfigFactory.parseString(
      s"""
         |akka.actor.warn-about-java-serializer-usage = off
         |""".stripMargin
    )
  }

  private def identityUrlShortener(): Config = {
    ConfigFactory.parseString(
      s"""
         |verity.services.url-shortener-service.selected = IDENTITY
         |""".stripMargin
    )
  }

  def standard(tempDir: Path,
               port: PortProfile,
               otherNodeArteryPorts: List[Int] = List.empty,
               taaEnabled: Boolean = true,
               taaAutoAccept: Boolean = true,
               sharedEventStore: Option[SharedEventStore]=None): Config = {
    val parts = Seq(
      useLevelDBPersistence(tempDir, sharedEventStore),
      useDefaultWallet(tempDir),
      changePoolName(),
      useCustomPort(port, otherNodeArteryPorts),
      turnOffWarnings(),
      messageSerialization,
      configureLibIndy(taaEnabled, taaAutoAccept),
      akkaConfig(),
      identityUrlShortener(),
      ConfigFactory.load()
    )
    val t = parts.fold(ConfigFactory.empty())(_.withFallback(_).resolve())
    t
  }

}
