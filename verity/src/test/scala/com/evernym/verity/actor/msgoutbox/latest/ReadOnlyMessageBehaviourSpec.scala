package com.evernym.verity.actor.msgoutbox.latest

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.pattern.StatusReply
import com.evernym.verity.actor.StorageInfo
import com.evernym.verity.actor.msgoutbox.message.{MessageBehaviour, ReadOnlyMessageBehaviour}
import com.evernym.verity.actor.msgoutbox.message.MessageBehaviour.{AddMsgRespBase, Commands, MsgAdded}
import ReadOnlyMessageBehaviour.Commands.Get
import ReadOnlyMessageBehaviour.{GetMsgRespBase, Msg}
import com.evernym.verity.actor.testkit.TestAppConfig
import com.evernym.verity.actor.typed.BehaviourSpecBase
import com.evernym.verity.storage_services.{BucketLifeCycleUtil, StorageAPI}
import com.evernym.verity.testkit.BasicSpec
import com.evernym.verity.testkit.mock.blob_store.MockBlobStore
import com.evernym.{PolicyElements, RetentionPolicy}
import com.typesafe.config.{Config, ConfigFactory}

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration._


//will be used as a child actor of Outbox actor
class ReadOnlyMessageBehaviourSpec
  extends BehaviourSpecBase
    with BasicSpec {

  "OutgoingRouter" - {
    "when received a request for outgoing message" - {

      "should be abe to store payload in external storage (S3)" in {
        storageAPI.getBlobObjectCount("20d", BUCKET_NAME) shouldBe 0
        val storePayload = storageAPI.put(
          BUCKET_NAME,
          msgIdLifeCycleAddress,
          "cred-offer-msg".getBytes
        )
        Await.result(storePayload, 5.seconds)
        storageAPI.getBlobObjectCount("20d", BUCKET_NAME) shouldBe 1
      }

      "should be able to store the message" in {
        val probe = createTestProbe[StatusReply[AddMsgRespBase]]()
        messageRegion ! ShardingEnvelope(msgId,
          Commands.Add("credOffer", None, retentionPolicy, StorageInfo(msgId, "s3"), Set("outbox-id-1"), probe.ref))
        probe.expectMessage(StatusReply.success(MsgAdded))
      }
    }
  }

  "ReadOnlyMessage behaviour" - {

    "when sent Get command for first time" - {
      "should be successful" in {
        val probe = createTestProbe[StatusReply[GetMsgRespBase]]()
        readOnlyActor ! Get(probe.ref)
        val msg = probe.expectMessageType[StatusReply[Msg]].getValue
        msg.payload.map(p => new String(p)) shouldBe Option("cred-offer-msg")
      }
    }

    "when sent Get command few more times" - {
      "should be successful" in {
        (1 to 5).foreach { _ =>
          val probe = createTestProbe[StatusReply[GetMsgRespBase]]()
          readOnlyActor ! Get(probe.ref)
          val msg = probe.expectMessageType[StatusReply[Msg]].getValue
          msg.payload.map(p => new String(p)) shouldBe Option("cred-offer-msg")
        }
      }
    }
  }

  lazy val msgId: String = UUID.randomUUID().toString
  lazy val retentionPolicy: RetentionPolicy = RetentionPolicy(
    """{"expire-after-days":20 days,"expire-after-terminal-state":true}""",
    PolicyElements(Duration.apply(20, DAYS), expireAfterTerminalState = true)
  )

  lazy val msgIdLifeCycleAddress: String = BucketLifeCycleUtil.lifeCycleAddress(
    Option(retentionPolicy.elements.expiryDaysStr), msgId)

  lazy val BUCKET_NAME = "blob-name"

  lazy val APP_CONFIG: Config = ConfigFactory.parseString (
    s"""
      |verity.blob-store.storage-service = "com.evernym.verity.testkit.mock.blob_store.MockBlobStore"
      |verity.blob-store.bucket-name = "$BUCKET_NAME"
      |""".stripMargin
  )

  val appConfig = new TestAppConfig(Option(APP_CONFIG), clearValidators = true)
  lazy val storageAPI: MockBlobStore = StorageAPI.loadFromConfig(appConfig)(system.classicSystem).asInstanceOf[MockBlobStore]
  lazy val sharding: ClusterSharding = ClusterSharding(system)

  lazy val readOnlyActor: ActorRef[ReadOnlyMessageBehaviour.Cmd] = spawn(ReadOnlyMessageBehaviour(msgId, BUCKET_NAME, storageAPI))

  lazy val messageRegion: ActorRef[ShardingEnvelope[MessageBehaviour.Cmd]] =
    sharding.init(Entity(MessageBehaviour.TypeKey) { entityContext =>
      MessageBehaviour(entityContext, BUCKET_NAME, storageAPI)
    })

}