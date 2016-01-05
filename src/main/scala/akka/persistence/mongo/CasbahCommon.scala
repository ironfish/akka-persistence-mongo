/**
  *  Copyright (C) 2015-2016 Duncan DeVore. <https://github.com/ironfish/>
  */
package akka.persistence.mongo

import com.mongodb.casbah
import com.mongodb.casbah.Imports._
import com.typesafe.config.Config

object CasbahCommon {
  val persistenceIdKey = "persistenceId"
  val sequenceNrKey = "sequenceNr"
  val messageKey: String = "message"
  val markerKey: String = "marker"
  val lteKey = "$lte"
  val gteKey: String = "$gte"
}

trait CasbahCommon {
  protected val initError: String = "Cannot get %s, out of the mongodb URI %s, probably invalid format."
  protected def mongoUrlKey: String = "mongo-url"
  protected val databaseMsg: String = "database"
  protected val collectionMsg: String = "collection"

  protected val configRootKey: String

  protected val config: Config

  private lazy val mongolUrl: String = config.getString(mongoUrlKey)

  private lazy val clientURI: casbah.MongoClientURI = MongoClientURI(mongolUrl)

  lazy val client: MongoClient = MongoClient(clientURI)

  lazy val mongoDB: MongoDB =
    client(clientURI.database.getOrElse(
      throw new IllegalArgumentException(initError.format(databaseMsg, clientURI.toString()))))

  lazy val mongoCollection: MongoCollection =
    mongoDB(clientURI.collection.getOrElse(
      throw new IllegalArgumentException(initError.format(collectionMsg, clientURI.toString()))))
}

trait CasbahJournalCommon extends CasbahCommon {
  override protected val configRootKey: String = "casbah-journal"
  protected def rejectNonSerializableObjectsKey: String = "reject-non-serializable-objects"
  lazy val rejectNonSerializableObjectId: Boolean = config.getBoolean(rejectNonSerializableObjectsKey)
}

trait CasbahSnapshotCommon extends CasbahCommon {
  override protected val configRootKey: String = "casbah-snapshot"
  val snapshotKey: String = "snapshot"
  val timestampKey = "timestamp"
}
