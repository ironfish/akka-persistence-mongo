/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.mongo.snapshot

import akka.persistence.{SnapshotSelectionCriteria, SnapshotMetadata, SelectedSnapshot}

import akka.persistence.mongo.MongoPersistenceSnapshotRoot

import com.mongodb.casbah.Imports._

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

  def writeJSON(snapshot: SelectedSnapshot) = {
    val builder = MongoDBObject.newBuilder
    builder += PersistenceIdKey -> snapshot.metadata.persistenceId
    builder += SequenceNrKey  -> snapshot.metadata.sequenceNr
    builder += TimestampKey   -> snapshot.metadata.timestamp
    builder += SnapshotKey    -> toBytes(snapshot)
    builder.result()
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
