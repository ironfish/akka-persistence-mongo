/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.journal.mongo

import com.mongodb.casbah.Imports._

trait CasbahHelper {

  val ProcessorIdKey = "processorId"
  val SequenceNrKey = "sequenceNr"
  val AggIdKey = "_id"
  val AddDetailsKey = "details"
  val MarkerKey = "marker"
  val MessageKey = "message"
  val MarkerAccepted = "A"
  val MarkerConfirmPrefix = "C"
  def markerConfirm(cId: String) = s"C-$cId"
  def markerConfirmParsePrefix(cId: String) = cId.substring(0,1)
  def markerConfirmParseSuffix(cId: String) = cId.substring(2)
  val MarkerDelete = "D"
  
  def writeJSON(pId: String, sNr: Long, msg: Array[Byte]) = {
    val builder = MongoDBObject.newBuilder
    builder += ProcessorIdKey -> pId
    builder += SequenceNrKey  -> sNr
    builder += MarkerKey      -> MarkerAccepted
    builder += MessageKey     -> msg
    builder.result()
  }

  def confirmJSON(pId: String, sNr: Long, cId: String) = {
    val builder = MongoDBObject.newBuilder
    builder += ProcessorIdKey -> pId
    builder += SequenceNrKey  -> sNr
    builder += MarkerKey      -> markerConfirm(cId)
    builder += MessageKey     -> Array.empty[Byte]
    builder.result()
  }

  def deleteMarkJSON(pId: String, sNr: Long) = {
    val builder = MongoDBObject.newBuilder
    builder += ProcessorIdKey -> pId
    builder += SequenceNrKey  -> sNr
    builder += MarkerKey      -> MarkerDelete
    builder += MessageKey     -> Array.empty[Byte]
    builder.result()
  }

  def deleteQueryStatement(processorId: String, sequenceNr: Long): MongoDBObject =
    MongoDBObject(ProcessorIdKey -> processorId, SequenceNrKey -> sequenceNr)

  def matchStatement(processorId: String, fromSequenceNr: Long, toSequenceNr: Long): MongoDBObject =
    MongoDBObject("$match" ->
      MongoDBObject(
        ProcessorIdKey -> processorId,
        SequenceNrKey  -> MongoDBObject("$gte" -> fromSequenceNr, "$lte" -> toSequenceNr)))

  def groupStatement: MongoDBObject =
    MongoDBObject("$group" ->
      MongoDBObject(
        AggIdKey     -> MongoDBObject(ProcessorIdKey -> "$processorId", SequenceNrKey -> "$sequenceNr"),
        AddDetailsKey -> MongoDBObject("$addToSet" -> MongoDBObject(MarkerKey -> "$marker", MessageKey -> "$message"))))

  def sortStatement: MongoDBObject = MongoDBObject("$sort" -> MongoDBObject(AggIdKey -> 1))

  def maxSnrQueryStatement(processorId: String): MongoDBObject = MongoDBObject(ProcessorIdKey -> processorId)
  def maxSnrSortStatement: MongoDBObject = MongoDBObject(SequenceNrKey -> -1)
}
