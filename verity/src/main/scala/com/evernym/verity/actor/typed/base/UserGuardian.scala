package com.evernym.verity.actor.typed.base

import java.util.concurrent.TimeUnit

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.ClusterShardingSettings
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import com.evernym.verity.util2.RetentionPolicy
import com.evernym.verity.actor.ActorMessage
import com.evernym.verity.actor.agent.AgentActorContext
import com.evernym.verity.actor.base.EntityIdentifier
import com.evernym.verity.config.ConfigConstants.PERSISTENT_ACTOR_BASE
import com.evernym.verity.msgoutbox.message_meta.MessageMeta
import com.evernym.verity.msgoutbox.outbox.Outbox
import com.evernym.verity.msgoutbox.outbox.msg_store.MsgStore
import com.evernym.verity.msgoutbox.outbox.msg_packager.{MsgPackagers, didcom_v1}
import com.evernym.verity.msgoutbox.outbox.msg_packager.didcom_v1.{DIDCommV1Packager, WalletOpExecutor}
import com.evernym.verity.msgoutbox.outbox.msg_transporter.{HttpTransporter, MsgTransports}
import com.evernym.verity.msgoutbox.rel_resolver.RelationshipResolver
import com.evernym.verity.msgoutbox.router.OutboxRouter
import com.evernym.verity.config.{AppConfig, ConfigUtil}
import com.evernym.verity.protocol.engine.ParticipantId

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration


//top level typed user guardian actor
// all user typed actors should be children of this one
object UserGuardian {

  sealed trait Cmd extends ActorMessage
  val defaultMessageMetaPassivationTimeoutInSeconds: Int = 60
  val defaultOutboxPassivationTimeoutInSeconds: Int = 300

  object Commands {
    case class SendMsgToOutbox(fromParticipantId: ParticipantId,
                               toParticipantId: ParticipantId,
                               msg: String,
                               msgType: String,
                               retentionPolicy: RetentionPolicy) extends Cmd

  }

  def apply(agentActorContext: AgentActorContext, executionContext: ExecutionContext): Behavior[Cmd] = {
    Behaviors.setup { actorContext =>
      val appConfig: AppConfig = agentActorContext.appConfig
      val sharding: ClusterSharding = ClusterSharding(actorContext.system)

      val msgStore: ActorRef[MsgStore.Cmd] = {
        val blobStoreBucket: String = appConfig
          .config
          .getConfig("verity.blob-store")
          .getString("bucket-name")

        actorContext.spawn(MsgStore(blobStoreBucket, agentActorContext.storageAPI, executionContext), "msg-store")
      }

      val relResolver: Behavior[RelationshipResolver.Cmd] = RelationshipResolver(agentActorContext.agentMsgRouter)

      val msgPackagers: MsgPackagers = new MsgPackagers {
        override val didCommV1Packager: Behavior[DIDCommV1Packager.Cmd] = {
          val walletOpExecutor: Behavior[WalletOpExecutor.Cmd] = didcom_v1.WalletOpExecutor(agentActorContext.walletAPI)
          DIDCommV1Packager(agentActorContext.agentMsgTransformer, walletOpExecutor, agentActorContext.metricsWriter, executionContext)
        }
      }

      sharding.init(Entity(MessageMeta.TypeKey) { entityContext =>
        MessageMeta(
          entityContext,
          msgStore,
          appConfig
        )
      }.withSettings(
        ClusterShardingSettings(actorContext.system)
          .withPassivateIdleEntityAfter(
            FiniteDuration(ConfigUtil.getReceiveTimeout(
              appConfig,
              defaultMessageMetaPassivationTimeoutInSeconds,
              PERSISTENT_ACTOR_BASE,
              MessageMeta.TypeKey.name,
              null
            ).toSeconds, TimeUnit.SECONDS))))

      sharding.init(Entity(Outbox.TypeKey) { entityContext =>
        val msgTransports: MsgTransports = new MsgTransports {
          override val httpTransporter: Behavior[HttpTransporter.Cmd] = HttpTransporter.apply(agentActorContext.msgSendingSvc, executionContext)
        }
        Outbox(
          entityContext,
          appConfig.config,
          agentActorContext.oAuthAccessTokenRefreshers,
          relResolver,
          msgStore,
          msgPackagers,
          msgTransports,
          executionContext
        )
      }.withSettings(
        ClusterShardingSettings(actorContext.system)
          .withPassivateIdleEntityAfter(
            FiniteDuration(ConfigUtil.getReceiveTimeout(
              appConfig,
              defaultOutboxPassivationTimeoutInSeconds,
              PERSISTENT_ACTOR_BASE,
              Outbox.TypeKey.name,
              null,
            ).toSeconds, TimeUnit.SECONDS)
          )))

      initialized(relResolver, msgStore)(actorContext)
    }
  }

  def initialized(relResolver: Behavior[RelationshipResolver.Cmd],
                  msgStore: ActorRef[MsgStore.Cmd])
                 (implicit actorContext: ActorContext[Cmd]): Behavior[Cmd] = Behaviors.receiveMessage {
    case sendMsg: Commands.SendMsgToOutbox =>
      val actorRef = actorContext.spawnAnonymous(
        OutboxRouter(
          sendMsg.fromParticipantId,
          sendMsg.toParticipantId,
          sendMsg.msg,
          sendMsg.msgType,
          sendMsg.retentionPolicy,
          relResolver,
          msgStore
        ))
      actorRef ! OutboxRouter.Commands.SendMsg
      Behaviors.same
  }
}


