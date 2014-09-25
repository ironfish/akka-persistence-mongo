/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.mongo.snapshot

import akka.persistence.{SnapshotSelectionCriteria, SnapshotMetadata, SelectedSnapshot}

import akka.persistence.serialization.Snapshot

import akka.persistence.mongo.MongoPersistenceSnapshotRoot

import com.mongodb.casbah.Imports._

import scala.util.Try

private[mongo] trait CasbahSnapshotHelper extends MongoPersistenceSnapshotRoot {

  val PersistenceIdKey = "persistenceId"
  val SequenceNrKey = "sequenceNr"
  val TimestampKey = "timestamp"
  val SnapshotKey = "snapshot"

  private[this] val snapIdx1 = MongoDBObject(
    "persistenceId"       -> 1,
    "sequenceNr"          -> 1,
    "timestamp"           -> 1)

  private[this] val snapIdx1Options =
    MongoDBObject("unique" -> true)

  private[this] val uri = MongoClientURI(configMongoSnapshotUrl)
  val client =  MongoClient(uri)
  private[this] val db = client(uri.database.getOrElse(throw new Exception("Cannot get database out of the mongodb URI, probably invalid format")))
  val collection = db(uri.collection.getOrElse(throw new Exception("Cannot get collection out of the mongodb URI, probably invalid format")))

  collection.ensureIndex(snapIdx1, snapIdx1Options)

  def writeJSON(metadata: SnapshotMetadata, snapshot: Any) = {
    val builder = MongoDBObject.newBuilder
    builder += PersistenceIdKey -> metadata.persistenceId
    builder += SequenceNrKey  -> metadata.sequenceNr
    builder += TimestampKey   -> metadata.timestamp
    builder += SnapshotKey    -> toBytes(Snapshot(snapshot))
    builder.result()
  }

  def readJSON(dbObject: MongoDBObject): Try[SelectedSnapshot] = {
    Try(fromBytes[Snapshot](dbObject.as[Array[Byte]](SnapshotKey))).map { snapshot =>
      val metadata = SnapshotMetadata(
        dbObject.as[String](PersistenceIdKey),
        dbObject.as[Long](SequenceNrKey),
        dbObject.as[Long](TimestampKey)
      )
      SelectedSnapshot(metadata, snapshot.data)
    }
  }

  def delStatement(meta: SnapshotMetadata): MongoDBObject =
    MongoDBObject(PersistenceIdKey -> meta.persistenceId, SequenceNrKey -> meta.sequenceNr, TimestampKey -> meta.timestamp)

  def delStatement(persistenceId: String, criteria: SnapshotSelectionCriteria): MongoDBObject =
    maxSnrMaxTimeQueryStatement(persistenceId, criteria)

  def snapshotsQueryStatement(persistenceId: String, criteria: SnapshotSelectionCriteria): MongoDBObject =
    maxSnrMaxTimeQueryStatement(persistenceId, criteria)

  def snapshotsSortStatement: MongoDBObject =
    MongoDBObject(SequenceNrKey -> -1, TimestampKey -> -1)

  private def maxSnrMaxTimeQueryStatement(persistenceId: String, criteria: SnapshotSelectionCriteria): MongoDBObject =
    MongoDBObject(
      PersistenceIdKey -> persistenceId,
      SequenceNrKey  -> MongoDBObject("$lte" -> criteria.maxSequenceNr),
      TimestampKey   -> MongoDBObject("$lte" -> criteria.maxTimestamp)
    )

  def casbahSnapshotWriteConcern: WriteConcern = configMongoSnapshotWriteConcern
}
