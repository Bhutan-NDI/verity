package com.evernym.verity.http.common

import java.util.UUID
import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpMethod, HttpMethods, HttpRequest, HttpResponse, MediaTypes, ResponseEntity, StatusCodes}
import akka.http.scaladsl.model.StatusCodes.{Accepted, BadRequest, GatewayTimeout, OK}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.scaladsl.{Sink, Source}
import com.evernym.verity.config.CommonConfig._
import com.evernym.verity.Exceptions.HandledErrorException
import com.evernym.verity.ExecutionContextProvider.futureExecutionContext
import com.evernym.verity.Status.{BAD_REQUEST, StatusDetail, UNHANDLED}
import com.evernym.verity.logging.LoggingUtil.getLoggerByClass
import com.evernym.verity.util.Util.buildHandledError
import com.evernym.verity.{Exceptions, UrlParam}
import com.evernym.verity.actor.wallet.PackedMsg
import com.evernym.verity.agentmsg.DefaultMsgCodec
import com.evernym.verity.config.AppConfig
import com.evernym.verity.metrics.{ClientSpan, MetricsWriterExtensionImpl}
import com.typesafe.scalalogging.Logger

import scala.concurrent.Future
import scala.util.{Left, Success, Try}


class AkkaHttpMsgSendingSvc(appConfig: AppConfig, metricsWriter: MetricsWriterExtensionImpl)
                           (implicit system: ActorSystem) extends MsgSendingSvc {

  //TODO: we should change the below 'None' case behavior to either
  // 'sendByRequestLevelFlowAPI' or 'sendByRequestLevelFutureAPI'
  // once we have tested those api types and sure that it doesn't break any thing
  protected val sendRequest = appConfig.getStringOption(AKKA_HTTP_MSG_SENDING_SVC_API_TYPE) match {
    case None                               => sendByConnectionLevelFlowAPI _
    case Some("connection-level-flow-api")  => sendByConnectionLevelFlowAPI _
    case Some("request-level-flow-api")     => sendByRequestLevelFlowAPI _
    case Some("request-level-future-api")   => sendByRequestLevelFutureAPI _
    case Some(apiType)                      => throw new RuntimeException("akka http client api not supported: " + apiType)
  }

  def sendPlainTextMsg(payload: String, method: HttpMethod = HttpMethods.POST)
                      (implicit up: UrlParam): Future[Either[HandledErrorException, String]] = {
    metricsWriter.get().runWithSpan("sendPlainTextMsg", getClass.getSimpleName, ClientSpan) {
      val req = HttpRequest(
        method = method,
        uri = up.url,
        entity = HttpEntity(payload)
      )
      sendRequestAndHandleResponse(req) { response =>
        val prp = performResponseParsing[String]
        prp(response)
      }
    }
  }

  def sendJsonMsg(payload: String)
                 (implicit up: UrlParam): Future[Either[HandledErrorException, String]] = {
    metricsWriter.get().runWithSpan("sendJsonMsg", getClass.getSimpleName, ClientSpan) {
      val req = HttpRequest(
        method = HttpMethods.POST,
        uri = up.url,
        entity = HttpEntity(MediaTypes.`application/json`, payload)
      )
      sendRequestAndHandleResponse(req) { response =>
        val prp = performResponseParsing[String]
        prp(response)
      }
    }
  }

  def sendBinaryMsg(payload: Array[Byte])
                   (implicit up: UrlParam): Future[Either[HandledErrorException, PackedMsg]] = {
    metricsWriter.get().runWithSpan("sendBinaryMsg", getClass.getSimpleName, ClientSpan) {
      val req = HttpRequest(
        method = HttpMethods.POST,
        uri = up.url,
        entity = HttpEntity(HttpCustomTypes.MEDIA_TYPE_SSI_AGENT_WIRE, payload)
      )
      sendRequestAndHandleResponse(req) { response =>
        import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers.byteArrayUnmarshaller
        val prp = performResponseParsing[Array[Byte]]
        prp(response).map(_.map(bd => PackedMsg(bd)))
      }
    }
  }

  private def sendRequestAndHandleResponse[T](req: HttpRequest)
                                             (respHandler: HttpResponse => Future[Either[HandledErrorException, T]])
                                             (implicit up: UrlParam):
  Future[Either[HandledErrorException, T]] = {
    val id = UUID.randomUUID().toString
    logger.info(s"[$id] [outgoing request] [${req.method.value}] to uri ${up.host}:${up.port}/${up.path}")
    sendRequest(req).flatMap { response =>
      logger.info(s"[$id] [incoming response] [${response.status}]")
      respHandler(response)
    }.recover {
      case e: Exception =>
        logger.info(s"[$id] [incoming response] [Error: ${e.getMessage}]")
        throw e
    }
  }

  private def logger: Logger = getLoggerByClass(getClass)

  //this does have some overhead as it will create a new connection for each request
  private def sendByConnectionLevelFlowAPI(request: HttpRequest): Future[HttpResponse] = {
    val up = UrlParam(request.uri.toString)
    val connectionFlow =
      if (up.isHttps) Http().outgoingConnectionHttps(up.host, up.port)
      else Http().outgoingConnection(up.host, up.port)
    Source.single(request).via(connectionFlow).runWith(Sink.head)
  }

  //this internally uses connection pool and hence recommended by akka
  private def sendByRequestLevelFutureAPI(request: HttpRequest): Future[HttpResponse] = {
    Http().singleRequest(request).recover {
      case e =>
        logger.error(Exceptions.getStackTraceAsSingleLineString(e))
        val errMsg = s"connection not established with remote server: ${request.uri}"
        logger.error(errMsg)
        HttpResponse(StatusCodes.custom(GatewayTimeout.intValue, errMsg, errMsg))
    }
  }

  //this internally uses connection pool and hence recommended by akka
  private def sendByRequestLevelFlowAPI(request: HttpRequest): Future[HttpResponse] = {
    Source.single((request, NotUsed)).via(superPoolFlow)
      .runWith(Sink.head)
      .map {
        case (Success(resp), NotUsed) => resp
        case _                        => throw new RuntimeException("")
      }
  }
  private val superPoolFlow = Http().superPool[NotUsed]()

  protected def performResponseParsing[T](implicit up: UrlParam, um: Unmarshaller[ResponseEntity, T]):
  PartialFunction[HttpResponse, Future[Either[HandledErrorException, T]]] = {
    case hr: HttpResponse if List(OK, Accepted).contains(hr.status) =>
      logger.debug(s"successful response ('${hr.status.value}') received from '${up.url}'")
      Unmarshal(hr.entity).to[T].map(Right(_))

    case hr: HttpResponse if hr.status ==  BadRequest =>
      Unmarshal(hr.entity).to[String].map { respMsg =>
        val errorMsg = buildStatusDetail(respMsg).map(_.toString).getOrElse(respMsg)
        val error = s"error response ('${hr.status.value}') received from '${up.url}': $errorMsg"
        logger.warn(error)
        Left(buildHandledError(BAD_REQUEST.withMessage(error))) }

    case hr: HttpResponse =>
      Unmarshal(hr.entity).to[String].map { respMsg =>
        val errorMsg = buildStatusDetail(respMsg).map(_.toString).getOrElse(respMsg)
        val error = s"error response ('${hr.status.value}') received from '${up.url}': $errorMsg"
        logger.warn(error)
        Left(buildHandledError(UNHANDLED.withMessage(error)))
      }
  }

  private def buildStatusDetail(resp: String): Option[StatusDetail] = Try{
    val sd = DefaultMsgCodec.fromJson[StatusDetail](resp)
    if (Option(sd.statusCode).isDefined && Option(sd.statusMsg).isDefined) {
      Option(sd)
    } else None
  }.getOrElse(None)
}


trait MsgSendingSvc {
  def sendPlainTextMsg(payload: String, method: HttpMethod = HttpMethods.POST)
                      (implicit up: UrlParam): Future[Either[HandledErrorException, String]]
  def sendJsonMsg(payload: String)
                 (implicit up: UrlParam): Future[Either[HandledErrorException, String]]
  def sendBinaryMsg(payload: Array[Byte])
                   (implicit up: UrlParam): Future[Either[HandledErrorException, PackedMsg]]
}
