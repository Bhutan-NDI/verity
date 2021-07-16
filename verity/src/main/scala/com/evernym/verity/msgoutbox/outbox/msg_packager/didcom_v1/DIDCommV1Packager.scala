package com.evernym.verity.msgoutbox.outbox.msg_packager.didcom_v1

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.evernym.verity.util2.ExecutionContextProvider.futureExecutionContext
import com.evernym.verity.actor.ActorMessage
import com.evernym.verity.actor.agent.MsgPackFormat
import com.evernym.verity.msgoutbox.outbox.msg_packager.didcom_v1.DIDCommV1Packager.Commands.{PackMsg, TimedOut, WalletOpExecutorReplyAdapter}
import com.evernym.verity.msgoutbox.outbox.msg_packager.didcom_v1.WalletOpExecutor.Replies.PackagedPayload
import com.evernym.verity.msgoutbox.{RecipPackaging, RoutePackaging, VerKey, WalletId}
import com.evernym.verity.agentmsg.msgpacker.{AgentMsgPackagingUtil, AgentMsgTransformer}
import com.evernym.verity.vault.WalletAPIParam

import scala.concurrent.duration.DurationInt

object DIDCommV1Packager {

  trait Cmd extends ActorMessage

  object Commands {
    case class PackMsg(msgType: String,
                       msgPayload: Array[Byte],
                       recipPackaging: RecipPackaging,
                       routePackaging: Option[RoutePackaging],
                       walletId: WalletId,
                       senderVerKey: VerKey,
                       replyTo: ActorRef[Reply]) extends Cmd
    case class WalletOpExecutorReplyAdapter(reply: WalletOpExecutor.Reply) extends Cmd
    case object TimedOut extends Cmd
  }

  trait Reply extends ActorMessage

  object Replies {
    case class PackedMsg(msg: Array[Byte]) extends Reply
  }

  def apply(agentMsgTransformer: AgentMsgTransformer,
            walletOpExecutor: Behavior[WalletOpExecutor.Cmd]): Behavior[Cmd] = {
    Behaviors.setup { actorContext =>
      actorContext.setReceiveTimeout(10.seconds, Commands.TimedOut) //TODO: finalize this
      val walletOpExecutorReplyAdapter = actorContext.messageAdapter(reply => WalletOpExecutorReplyAdapter(reply))
      initialized(agentMsgTransformer, walletOpExecutor)(actorContext, walletOpExecutorReplyAdapter)
    }
  }

  def initialized(agentMsgTransformer: AgentMsgTransformer,
                  walletOpExecutor: Behavior[WalletOpExecutor.Cmd])
                 (implicit actorContext: ActorContext[Cmd],
                  walletOpExecutorReplyAdapter: ActorRef[WalletOpExecutor.Reply]): Behavior[Cmd] = Behaviors.receiveMessage {

    case PackMsg(msgType, msgPayload, recipPackaging, routePackaging, walletId, senderVerKey, replyTo) =>
      val walletOpExecutorRef = actorContext.spawnAnonymous(walletOpExecutor)
      walletOpExecutorRef ! WalletOpExecutor.Commands.PackMsg(
        msgPayload, recipPackaging.recipientKeys.toSet, senderVerKey, walletId, walletOpExecutorReplyAdapter)
      handleRecipPackedMsg(msgType, walletId, routePackaging, agentMsgTransformer, replyTo)

    case cmd => baseBehavior(cmd)
  }

  def handleRecipPackedMsg(msgType: String,
                           walletId: WalletId,
                           routePackaging: Option[RoutePackaging],
                           agentMsgTransformer: AgentMsgTransformer,
                           replyTo: ActorRef[Reply]): Behavior[Cmd] = Behaviors.receiveMessage {
    case WalletOpExecutorReplyAdapter(reply: PackagedPayload) =>
      routePackaging match {
        case None =>
          replyTo ! Replies.PackedMsg(reply.payload)
          Behaviors.stopped
        case Some(rp) =>
          val msgPackFormat = MsgPackFormat.fromString(rp.pkgType)
          val routingKeys = AgentMsgPackagingUtil.buildRoutingKeys(
            rp.recipientKeys.head,
            rp.routingKeys)
          val future = AgentMsgPackagingUtil.packMsgForRoutingKeys(
            msgPackFormat,
            reply.payload,
            routingKeys,
            msgType
          )(agentMsgTransformer, WalletAPIParam(walletId))
          future.map { pm =>
            replyTo ! Replies.PackedMsg(pm.msg)
          }
          Behaviors.stopped
      }

    case cmd => baseBehavior(cmd)
  }

  private def baseBehavior: PartialFunction[Cmd, Behavior[Cmd]] = {
    case TimedOut => Behaviors.stopped
  }
}