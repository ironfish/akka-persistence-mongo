/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.mongo.snapshot

import akka.persistence.{SnapshotSelectionCriteria, SnapshotMetadata, SelectedSnapshot}

import akka.persistence.mongo.MongoPersistenceSnapshotRoot

import com.mongodb.casbah.Imports._

private[mongo] trait CasbahSnapshotHelper extends MongoPersistenceSnapshotRoot {

  val ProcessorIdKey = "processorId"
  val SequenceNrKey = "sequenceNr"
  val TimestampKey = "timestamp"
  val SnapshotKey = "snapshot"

  private[this] val snapIdx1 = MongoDBObject(
    "processorId"         -> 1,
    "sequenceNr"          -> 1,
    "timestamp"           -> 1)

  private[this] val snapIdx1Options =
    MongoDBObject("unique" -> true)

  private[this] val uri = MongoClientURI(configMongoSnapshotUrl)
  val client =  MongoClient(uri)
  private[this] val db = client(uri.database.get)
  val collection = db(uri.collection.get)

  collection.ensureIndex(snapIdx1, snapIdx1Options)

  def writeJSON(snapshot: SelectedSnapshot) = {
    val builder = MongoDBObject.newBuilder
    builder += ProcessorIdKey -> snapshot.metadata.processorId
    builder += SequenceNrKey  -> snapshot.metadata.sequenceNr
    builder += TimestampKey   -> snapshot.metadata.timestamp
    builder += SnapshotKey    -> toBytes(snapshot)
    builder.result()
  }

  def delStatement(meta: SnapshotMetadata): MongoDBObject =
    MongoDBObject(ProcessorIdKey -> meta.processorId, SequenceNrKey -> meta.sequenceNr, TimestampKey -> meta.timestamp)

  def delStatement(processorId: String, criteria: SnapshotSelectionCriteria): MongoDBObject =
    maxSnrMaxTimeQueryStatement(processorId, criteria)

  def snapshotsQueryStatement(processorId: String, criteria: SnapshotSelectionCriteria): MongoDBObject =
    maxSnrMaxTimeQueryStatement(processorId, criteria)

  def snapshotsSortStatement: MongoDBObject =
    MongoDBObject(SequenceNrKey -> -1, TimestampKey -> -1)

  private def maxSnrMaxTimeQueryStatement(processorId: String, criteria: SnapshotSelectionCriteria): MongoDBObject =
    MongoDBObject(
      ProcessorIdKey -> processorId,
      SequenceNrKey  -> MongoDBObject("$lte" -> criteria.maxSequenceNr),
      TimestampKey   -> MongoDBObject("$lte" -> criteria.maxTimestamp)
    )

  def casbahSnapshotWriteConcern: WriteConcern = configMongoSnapshotWriteConcern
}
