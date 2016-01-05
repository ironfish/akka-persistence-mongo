/**
  *  Copyright (C) 2015-2016 Duncan DeVore. <https://github.com/ironfish/>
  */
package akka.persistence.mongo.snapshot

import akka.actor.ActorLogging
import akka.persistence.serialization.Snapshot
import akka.persistence.{SelectedSnapshot, SnapshotSelectionCriteria, SnapshotMetadata}
import akka.persistence.mongo.{CasbahRoot, CasbahCommon, CasbahSnapshotCommon}
import com.mongodb.casbah.Imports._

import scala.util.{Failure, Success}

trait CasbahSnapshotRoot extends CasbahRoot
  with CasbahSnapshotCommon { mixin : ActorLogging =>

  import CasbahCommon._

  private val loadAttemptsKey: String = "load-attempts"

  protected lazy val loadAttempts: Int = config.getInt(loadAttemptsKey)

  override protected def initialize(): Unit = {
    val indexOne: MongoDBObject = MongoDBObject(
      persistenceIdKey -> 1,
      sequenceNrKey -> 1,
      timestampKey -> 1)
    ensure(indexOne, indexOptions)(mongoCollection)
  }

  protected def deleteStatement(meta: SnapshotMetadata): MongoDBObject =
    MongoDBObject(
      persistenceIdKey -> meta.persistenceId,
      sequenceNrKey -> meta.sequenceNr)

  protected def deleteStatement(persistenceId: String, criteria: SnapshotSelectionCriteria): MongoDBObject =
    maxSequenceNrMaxTimeStatement(persistenceId, criteria)

  protected def loadStatement(persistenceId: String, criteria: SnapshotSelectionCriteria): MongoDBObject =
    maxSequenceNrMaxTimeStatement(persistenceId, criteria)


  protected def snapshotToDbObject(metadata: SnapshotMetadata, snapshot: Any): DBObject = {
    val builder = MongoDBObject.newBuilder
    builder += persistenceIdKey -> metadata.persistenceId
    builder += sequenceNrKey -> metadata.sequenceNr
    builder += snapshotKey -> toBytes(Snapshot(snapshot)).get // okay, will not be stored if serialization failed.
    builder += timestampKey -> metadata.timestamp
    builder.result()
  }

  protected def sortStatement: MongoDBObject =
    MongoDBObject(
      sequenceNrKey -> -1,
      timestampKey -> -1)

  def dbObjectToSelectedSnapshot(dbObject: DBObject): Option[SelectedSnapshot] = {

    def toSelectedSnapshot(dBObject: DBObject, snapshot: Snapshot): SelectedSnapshot = {
      val snapshotMetadata = SnapshotMetadata(
        dbObject.as[String](persistenceIdKey),
        dbObject.as[Long](sequenceNrKey),
        dbObject.as[Long](timestampKey))
      SelectedSnapshot(snapshotMetadata, snapshot.data)
    }

    fromBytes[Snapshot](dbObject,snapshotKey) match {
      case Success(snapshot) =>
        Some(toSelectedSnapshot(dbObject, snapshot))
      case Failure(error) =>
        log.error(error, s"error replaying snapshot: ${dbObject.toString}")
        None
    }
  }

  private def maxSequenceNrMaxTimeStatement(persistenceId: String, criteria: SnapshotSelectionCriteria): MongoDBObject =
    MongoDBObject(
      persistenceIdKey -> persistenceId,
      sequenceNrKey  -> MongoDBObject(lteKey -> criteria.maxSequenceNr),
      timestampKey   -> MongoDBObject(lteKey -> criteria.maxTimestamp))
}
