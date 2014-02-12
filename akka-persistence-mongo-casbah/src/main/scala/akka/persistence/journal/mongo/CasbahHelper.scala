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

  val idx1 = MongoDBObject(
    "processorId"         -> 1,
    "sequenceNr"          -> 1,
    "marker"              -> 1)

  val idx1Options =
    MongoDBObject("unique" -> true)

  val idx2 = MongoDBObject(
    "processorId"         -> 1,
    "sequenceNr"          -> 1)

  val idx3 =
    MongoDBObject("sequenceNr" -> 1)

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

  def delStatement(processorId: String, sequenceNr: Long): MongoDBObject =
    MongoDBObject(ProcessorIdKey -> processorId, SequenceNrKey -> sequenceNr)

  def delToStatement(processorId: String, toSequenceNr: Long): MongoDBObject =
    MongoDBObject(
      ProcessorIdKey -> processorId,
      SequenceNrKey  -> MongoDBObject("$lte" -> toSequenceNr))

  def delOrStatement(elements: List[MongoDBObject]): MongoDBObject =
    MongoDBObject("$or" -> elements)

  def matchStatement(processorId: String, fromSequenceNr: Long, toSequenceNr: Long): MongoDBObject =
    MongoDBObject("$match" ->
      MongoDBObject(
        ProcessorIdKey -> processorId,
        SequenceNrKey  -> MongoDBObject("$gte" -> fromSequenceNr, "$lte" -> toSequenceNr)))

  def groupStatement: MongoDBObject =
    MongoDBObject("$group" ->
      MongoDBObject(
        AggIdKey      -> MongoDBObject(ProcessorIdKey -> "$processorId", SequenceNrKey -> "$sequenceNr"),
        AddDetailsKey -> MongoDBObject("$addToSet" -> MongoDBObject(MarkerKey -> "$marker", MessageKey -> "$message"))))

  def sortStatement: MongoDBObject =
    MongoDBObject("$sort" -> MongoDBObject(AggIdKey -> 1))

  def snrQueryStatement(processorId: String): MongoDBObject =
    MongoDBObject(ProcessorIdKey -> processorId)

  def maxSnrSortStatement: MongoDBObject =
    MongoDBObject(SequenceNrKey -> -1)

  def minSnrSortStatement: MongoDBObject =
    MongoDBObject(SequenceNrKey -> 1)
}
