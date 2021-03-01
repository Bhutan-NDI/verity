package com.evernym.verity.vault.wallet_api

import akka.actor.ActorRef
import com.evernym.verity.vault.WalletAPIParam

import scala.concurrent.Future

trait WalletAPI {
  def executeAsync[T](cmd: Any)(implicit wap: WalletAPIParam): Future[T]
  def tell(cmd: Any)(implicit wap: WalletAPIParam, sender: ActorRef): Unit
}

