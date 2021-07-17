package com.evernym.verity.storage_services.leveldb

import akka.actor.ActorSystem
import akka.Done
import com.evernym.verity.util2.Exceptions.BadRequestErrorException
import com.evernym.verity.util2.ExecutionContextProvider.futureExecutionContext
import com.evernym.verity.util2.Status.DATA_NOT_FOUND
import com.evernym.verity.actor.StorageInfo
import com.evernym.verity.config.AppConfig
import com.evernym.verity.storage_services.StorageAPI
import org.iq80.leveldb.impl.Iq80DBFactory

import scala.concurrent.Future
import org.iq80.leveldb.{DB, Options}

import java.io.File

/**
 * This is used for verity instances running in a local environment. It has not been tested for high load volume. The purpose
 * is to have storage for a contained environment.
 */

//NOTE: if at all this file gets moved to different package, then it will require configuration change
// so until it is important, should avoid moving this to different package.

class LeveldbAPI(config: AppConfig)(implicit val as: ActorSystem) extends StorageAPI(config) {

  lazy val path: String = config.config.getConfig("verity.blob-store").getString("local-store-path")
  lazy val options: Options = new Options()
    .createIfMissing(true)
    .paranoidChecks(true)
    .verifyChecksums(true)

  def withDB[T](f: DB => T): T = synchronized {
    val db = Iq80DBFactory.factory.open(new File(path), options)
    val result = f(db)
    db.close()
    result
  }

  private def dbKey(bucketName: String, id: String): String = s"$bucketName-$id"

  /**
   * @param id needs to be unique or data can be overwritten
   */
  def put(bucketName: String, id: String, data: Array[Byte]): Future[StorageInfo] = {
    Future {
      withDB { db =>
        db.put(dbKey(bucketName, id).getBytes(), data)
        StorageInfo(dbKey(bucketName, id))
      }
    }
  }

  def get(bucketName: String, id: String): Future[Option[Array[Byte]]] = {
    withDB { db =>
      Option(db.get(dbKey(bucketName, id).getBytes())) match {
        case Some(x: Array[Byte]) => Future(Some(x))
        case None => failure(DATA_NOT_FOUND.statusCode, s"No object for id: $id in bucket: $bucketName")
      }
    }
  }

  def delete(bucketName: String, id: String): Future[Done] = {
    withDB { db =>
      db.delete(dbKey(bucketName, id).getBytes())
      Future(Done)
    }
  }

  def failure(code: String, msg: String): Future[Nothing] =
    Future.failed(new LeveldbFailure(code, Some(msg)))

  class LeveldbFailure(statusCode: String, statusMsg: Option[String] = None,
                       statusMsgDetail: Option[String] = None, errorDetail: Option[Any] = None)
    extends BadRequestErrorException(statusCode, statusMsg, statusMsgDetail, errorDetail)
}