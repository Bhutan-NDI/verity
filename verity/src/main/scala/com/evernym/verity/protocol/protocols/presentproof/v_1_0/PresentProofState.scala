package com.evernym.verity.protocol.protocols.presentproof.v_1_0

import com.evernym.verity.agentmsg.DefaultMsgCodec

trait Event

case class StateData(requests: List[ProofRequest] = List(),
                     proposals: List[Object] = List(),
                     presentation: Option[ProofPresentation] = None,
                     presentedAttributes: Option[AttributesPresented] = None,
                     verificationResults: Option[String] = None,
                     presentationAcknowledged: Boolean = false,
                     agentName: Option[String] = None,
                     logoUrl: Option[String] = None,
                     agencyVerkey: Option[String] = None,
                     publicDid: Option[String] = None) {

  def addPresentation(presentation: String): StateData = {
    copy(presentation = Some(DefaultMsgCodec.fromJson[ProofPresentation](presentation)))
  }

  def addAttributesPresented(given: String): StateData = {
    copy(presentedAttributes = Some(DefaultMsgCodec.fromJson[AttributesPresented](given)))
  }

  def addVerificationResults(results: String): StateData = {
    copy(verificationResults = Some(results))
  }


  def addAck(status: String): StateData = {
    val wasAcknowledged = status.toUpperCase() match {
      case "OK" => true
      case _ => false
    }
    copy(presentationAcknowledged = wasAcknowledged)
  }
}

sealed trait State
sealed trait HasData {
  def data: StateData
}

object States {
  // Common States
  case class Uninitialized() extends State
  case class Initialized(data: StateData) extends State
  case class ProblemReported(data: StateData, problemDescription: String) extends State with HasData
  case class Rejected(data: StateData, whoRejected: Role, reasonGiven: Option[String]) extends State with HasData

  // Verifier States
  case class ProposalReceived(data: StateData) extends State with HasData
  case class RequestSent(data: StateData) extends State with HasData
  case class Complete(data: StateData) extends State with HasData
  def initRequestSent(requestStr: String): RequestSent = {
    val req = DefaultMsgCodec.fromJson[ProofRequest](requestStr)
    RequestSent(StateData(requests = List(req)))
  }

  // Prover States
  case class RequestReceived(data: StateData) extends State with HasData
  case class ProposalSent(data: StateData) extends State with HasData
  case class Presented(data: StateData) extends State with HasData
  def initRequestReceived(requestStr: String): RequestReceived = {
    val req = DefaultMsgCodec.fromJson[ProofRequest](requestStr)
    RequestReceived(StateData(requests = List(req)))
  }

}