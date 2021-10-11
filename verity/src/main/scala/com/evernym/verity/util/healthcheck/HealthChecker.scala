package com.evernym.verity.util.healthcheck

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import com.evernym.verity.actor.Platform
import com.evernym.verity.actor.cluster_singleton.{GetValue, KeyValueMapper}
import com.evernym.verity.libindy.wallet.LibIndyWalletProvider
import com.evernym.verity.vault.WalletDoesNotExist
import com.evernym.verity.vault.WalletUtil.generateWalletParamAsync

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

case class ReadinessStatus(status: Boolean = false, rds: String = "", dynamoDB: String = "", storageAPI: String = "")

case class ApiStatus(status: Boolean, msg: String)


trait AbstractHealthChecker {
  def checkAkkaEventStorageReadiness: Future[ApiStatus]

  def checkWalletStorageReadiness: Future[ApiStatus]

  def checkStorageAPIReadiness: Future[ApiStatus]

  def checkLiveness: Future[Unit]
}

/**
 * Logic for this object based on com.evernym.verity.app_launcher.LaunchPreCheck methods
 */
class HealthChecker(val platform: Platform) extends AbstractHealthChecker {
  private implicit val as: ActorSystem = platform.actorSystem
  private implicit val ex: ExecutionContext = platform.executionContextProvider.futureExecutionContext

  override def checkAkkaEventStorageReadiness: Future[ApiStatus] = {
    implicit val timeout: Timeout = Timeout(Duration.create(15, TimeUnit.SECONDS))
    val actorId = "dummy-actor-" + UUID.randomUUID().toString
    val keyValueMapper = platform.aac.system.actorOf(KeyValueMapper.props(platform.executionContextProvider.futureExecutionContext)(platform.aac), actorId)
    val fut = {
      (keyValueMapper ? GetValue("dummy-key"))
        .mapTo[Option[String]]
        .transform( t => {
          platform.aac.system.stop(keyValueMapper)
          t match {
            case Success(_) => Success(ApiStatus(status = true, "OK"))
            case Failure(e) => Success(ApiStatus(status = false, e.getMessage))
          }
        })
    }
    fut
  }

  //TODO: this logic doesn't seem to be working, should come back to this and fix it
  override def checkWalletStorageReadiness: Future[ApiStatus] = {
    val walletId = "dummy-wallet-" + UUID.randomUUID().toString
    val wap = generateWalletParamAsync(walletId, platform.aac.appConfig, LibIndyWalletProvider)
    val fLibIndy = wap.flatMap(w => {
      LibIndyWalletProvider.openAsync(w.walletName, w.encryptionKey, w.walletConfig)
    })
    fLibIndy.map {
      _ => ApiStatus(status = true, "OK")
    } recover {
      case _: WalletDoesNotExist => ApiStatus(status = true, "OK")
      case e: Exception => ApiStatus(status = false, e.getMessage)
    }
  }

  override def checkStorageAPIReadiness: Future[ApiStatus] = {
    platform.aac.storageAPI.ping map {
      _ => ApiStatus(status = true, "OK")
    } recover {
      case e: Exception => ApiStatus(status = false, e.getMessage)
    }
  }

  //This method checks that Verity can respond to the liveness request,
  // and `Future{}` checks if ExecutionContext is available, and can execute Future.
  override def checkLiveness: Future[Unit] = {
    Future {}
  }

}