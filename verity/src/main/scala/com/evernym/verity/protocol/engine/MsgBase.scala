package com.evernym.verity.protocol.engine

import com.evernym.verity.constants.Constants.VALID_DID_BYTE_LENGTH
import com.evernym.verity.util.Base58Util

import scala.util.{Failure, Success, Try}

class EmptyValueForOptionalFieldProtocolEngineException(statusMsg: String)
  extends ProtocolEngineException(statusMsg)

class MissingReqFieldProtocolEngineException(statusMsg: String)
  extends ProtocolEngineException(statusMsg)

class InvalidFieldValueProtocolEngineException(statusMsg: String)
  extends ProtocolEngineException(statusMsg)

trait MsgBase {

  def validate(): Unit = {}

  def throwMissingReqFieldException(fieldName: String): Unit = {
    throw new MissingReqFieldProtocolEngineException(s"required attribute not found (missing/empty/null): '$fieldName'")
  }

  def throwOptionalFieldValueAsEmptyException(fieldName: String): Unit = {
    throw new EmptyValueForOptionalFieldProtocolEngineException(s"empty value given for optional field: '$fieldName'")
  }

  def throwInvalidFieldProtocolEngineException(fieldName: String, explanation: Option[String] = None): Unit = {
    val exp: String = explanation.map(e => s": $e").getOrElse("")
    throw new InvalidFieldValueProtocolEngineException(s"field '$fieldName' has invalid value$exp")
  }

  def checkIfValidBooleanData(fieldName: String, fieldValue: Option[Boolean]): Unit = {
    Try(fieldValue.getOrElse(false)).getOrElse(
      throwInvalidFieldProtocolEngineException(fieldName)
    )
  }

  def checkRequired(fieldName: String, fieldValue: Any, allowEmpty: Boolean = false): Unit = {
    // check if null
    if (Option(fieldValue).isEmpty) throwMissingReqFieldException(fieldName)
    fieldValue match {
      case mb: MsgBase => mb.validate()
      case s: String => if (!allowEmpty && s.trim.isEmpty) throwMissingReqFieldException(fieldName)
      case l: List[Any] =>
        if (!allowEmpty && l.isEmpty) throwMissingReqFieldException(fieldName)
        l.foreach(checkRequired(s"$fieldName item", _))
      case v: Vector[Any] =>
        if (!allowEmpty && v.isEmpty) throwMissingReqFieldException(fieldName)
        v.foreach(checkRequired(s"$fieldName item", _))
      case s: Seq[Any] =>
        if (!allowEmpty && s.isEmpty) throwMissingReqFieldException(fieldName)
        s.foreach(checkRequired(s"$fieldName item", _))
      case m: Map[_, _] =>
        if (!allowEmpty && m.isEmpty) throwMissingReqFieldException(fieldName)
        for ((key, value) <- m) {
          checkRequired(s"$fieldName key", key)
          checkRequired(s"$fieldName value", value, allowEmpty = true)
        }
      case _ =>
    }
  }

  def checkOptionalNotEmpty(fieldName: String, fieldValue: Option[Any]): Unit = {
    fieldValue match {
      case Some(s: String) => if (s.trim.isEmpty) throwOptionalFieldValueAsEmptyException(fieldName)
      case Some(mb: MsgBase) => mb.validate()
      case Some(_: Any) => //
      case _ => //
    }
  }

  def checkValidDID(fieldName: String, fieldValue: String): Unit = {
    if (fieldValue.length > 100) { // value that definitely cover 32 byte payload
      throwInvalidFieldProtocolEngineException(fieldName, Some("Value too long"))
    }
    val decoded = Base58Util.decode(fieldValue)
    decoded match {
      case Success(m) =>
        if (m.length != VALID_DID_BYTE_LENGTH)
          throwInvalidFieldProtocolEngineException(fieldName, Some("Invalid length"))
      case Failure(_) =>
        throwInvalidFieldProtocolEngineException(fieldName, Some("Unable to decode"))
    }
  }
}
