package com.evernym.verity.vdr.service

import akka.actor.ActorInitializationException
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.evernym.verity.actor.ActorMessage
import com.evernym.verity.did.DidStr
import com.evernym.verity.vdr.{FQCredDefId, FQDid, FQSchemaId, Namespace, VDRToolsFactoryParam}
import com.evernym.verity.vdr.service.VDRActor.Commands.{LedgersRegistered, Ping, PrepareCredDefTxn, PrepareSchemaTxn, ResolveCredDef, ResolveDID, ResolveSchema, SubmitTxn}
import com.evernym.verity.vdr.service.VDRActor.Replies.{PingResp, PrepareTxnResp, ResolveCredDefResp, ResolveDIDResp, ResolveSchemaResp, SubmitTxnResp}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

//interacts with provided VDR object
object VDRActor {

  sealed trait Cmd extends ActorMessage
  object Commands {
    case object LedgersRegistered extends Cmd

    case class Ping(namespaces: List[Namespace],
                    replyTo: ActorRef[Replies.PingResp]) extends Cmd

    case class PrepareSchemaTxn(schemaJson: String,
                                fqSchemaId: FQSchemaId,
                                submitterDID: DidStr,
                                endorser: Option[String],
                                replyTo: ActorRef[Replies.PrepareTxnResp]) extends Cmd

    case class PrepareCredDefTxn(credDefJson: String,
                                 fqCredDefId: FQCredDefId,
                                 submitterDID: DidStr,
                                 endorser: Option[String],
                                 replyTo: ActorRef[Replies.PrepareTxnResp]) extends Cmd

    case class SubmitTxn(preparedTxn: VDR_PreparedTxn,
                         signature: Array[Byte],
                         endorsement: Array[Byte],
                         replyTo: ActorRef[Replies.SubmitTxnResp]) extends Cmd

    case class ResolveSchema(schemaId: FQSchemaId,
                             replyTo: ActorRef[Replies.ResolveSchemaResp]) extends Cmd

    case class ResolveCredDef(credDefId: FQCredDefId,
                              replyTo: ActorRef[Replies.ResolveCredDefResp]) extends Cmd

    case class ResolveDID(fqDid: FQDid,
                          replyTo: ActorRef[Replies.ResolveDIDResp]) extends Cmd

  }

  trait Reply extends ActorMessage
  object Replies {
    case class PingResp(resp: Try[VDR_PingResult]) extends Reply
    case class PrepareTxnResp(preparedTxn: Try[VDR_PreparedTxn]) extends Reply
    case class SubmitTxnResp(preparedTxn: Try[VDR_SubmittedTxn]) extends Reply
    case class ResolveSchemaResp(resp: Try[VDR_Schema]) extends Reply
    case class ResolveCredDefResp(resp: Try[VDR_CredDef]) extends Reply
    case class ResolveDIDResp(resp: Try[VDR_DidDoc]) extends Reply
  }

  //implementation of above typed interface
  def apply(vdrToolsFactory: VDRToolsFactory,
            vdrToolsConfig: VDRToolsConfig,
            executionContext: ExecutionContext): Behavior[Cmd] = {
    Behaviors.setup { actorContext =>
      Behaviors.withStash(10) { buffer =>
        Behaviors.supervise {
          initializing(vdrToolsFactory, vdrToolsConfig)(buffer, actorContext, executionContext)
        }.onFailure[ActorInitializationException](SupervisorStrategy.stop)
      }
    }
  }

  def initializing(vdrToolsFactory: VDRToolsFactory,
                   vdrToolsConfig: VDRToolsConfig)
                  (implicit buffer: StashBuffer[Cmd],
                   actorContext: ActorContext[Cmd],
                   executionContext: ExecutionContext): Behavior[Cmd] = {
    val vdrTools = vdrToolsFactory(VDRToolsFactoryParam(vdrToolsConfig.libraryDirLocation))
    registerLedgers(vdrTools, vdrToolsConfig)
    waitingForLedgerRegistration(vdrTools)
  }

  def waitingForLedgerRegistration(vdrTools: VDRTools)
                                  (implicit  buffer: StashBuffer[Cmd],
                                   actorContext: ActorContext[Cmd],
                                   executionContext: ExecutionContext): Behavior[Cmd] =
    Behaviors.receiveMessage {
      case LedgersRegistered =>
        buffer.unstashAll(ready(vdrTools))

      case other       =>
        buffer.stash(other)
        Behaviors.same
    }

  def ready(vdrTools: VDRTools)(implicit executionContext: ExecutionContext): Behavior[Cmd] =
    Behaviors.receiveMessagePartial {
      case p: Ping                  => handlePing(vdrTools, p)
      case pst: PrepareSchemaTxn    => handlePrepareSchemaTxn(vdrTools, pst)
      case pcdt: PrepareCredDefTxn  => handlePrepareCredDefTxn(vdrTools, pcdt)
      case st: SubmitTxn            => handleSubmitTxn(vdrTools, st)

      case rs: ResolveSchema        => handleResolveSchema(vdrTools, rs)
      case rcd: ResolveCredDef      => handleResolveCredDef(vdrTools, rcd)
      case rd: ResolveDID           => handleResolveDID(vdrTools, rd)
    }

  private def handlePing(vdrTools: VDRTools,
                         p: Ping)
                        (implicit executionContext: ExecutionContext): Behavior[Cmd] = {
    vdrTools
      .ping(p.namespaces)
      .onComplete(resp => p.replyTo ! PingResp(resp))
    Behaviors.same
  }

  private def handlePrepareSchemaTxn(vdrTools: VDRTools,
                                     pst: PrepareSchemaTxn)
                                    (implicit executionContext: ExecutionContext): Behavior[Cmd] = {

    vdrTools
      .prepareSchemaTxn(pst.schemaJson, pst.fqSchemaId, pst.submitterDID, pst.endorser)
      .onComplete(resp => pst.replyTo ! PrepareTxnResp(resp))
    Behaviors.same
  }

  private def handlePrepareCredDefTxn(vdrTools: VDRTools,
                                      pcdt: PrepareCredDefTxn)
                                     (implicit executionContext: ExecutionContext): Behavior[Cmd] = {
    vdrTools
      .prepareCredDefTxn(pcdt.credDefJson, pcdt.fqCredDefId, pcdt.submitterDID, pcdt.endorser)
      .onComplete(resp => pcdt.replyTo ! PrepareTxnResp(resp))
    Behaviors.same
  }

  private def handleSubmitTxn(vdrTools: VDRTools,
                              st: SubmitTxn)
                             (implicit executionContext: ExecutionContext): Behavior[Cmd] = {
    vdrTools
      .submitTxn(st.preparedTxn, st.signature, st.endorsement)
      .onComplete(resp => st.replyTo ! SubmitTxnResp(resp))
    Behaviors.same
  }

  private def handleResolveSchema(vdrTools: VDRTools,
                                  rs: ResolveSchema)
                                 (implicit executionContext: ExecutionContext): Behavior[Cmd] = {
    vdrTools
      .resolveSchema(rs.schemaId)
      .onComplete(resp => rs.replyTo ! ResolveSchemaResp(resp))
    Behaviors.same
  }

  private def handleResolveCredDef(vdrTools: VDRTools,
                                   rcd: ResolveCredDef)
                                  (implicit executionContext: ExecutionContext): Behavior[Cmd]= {
    vdrTools
      .resolveCredDef(rcd.credDefId)
      .onComplete(resp => rcd.replyTo ! ResolveCredDefResp(resp))
    Behaviors.same
  }

  private def handleResolveDID(vdrTools: VDRTools,
                               rd: ResolveDID)
                              (implicit executionContext: ExecutionContext): Behavior[Cmd] = {
    vdrTools
      .resolveDID(rd.fqDid)
      .onComplete(resp => rd.replyTo ! ResolveDIDResp(resp))
    Behaviors.same
  }

  private def registerLedgers(vdrTools: VDRTools,
                              vdrToolsConfig: VDRToolsConfig)
                             (implicit actorContext: ActorContext[Cmd],
                              executionContext: ExecutionContext): Unit = {
    val futures = Future.sequence(
      vdrToolsConfig.ledgers.map {
        case il: IndyLedger =>
          vdrTools.registerIndyLedger(
            il.namespaces,
            il.genesisTxnFilePath,
            il.taaConfig
          )
      }
    )
    actorContext.pipeToSelf(futures) {
      case Success(_)   => LedgersRegistered
      case Failure(ex)  => throw ex
    }
  }
}