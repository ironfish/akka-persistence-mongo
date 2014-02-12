/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.journal.mongo

import akka.actor.ActorLogging
import akka.persistence._
import akka.persistence.journal.SyncWriteJournal
import akka.serialization.SerializationExtension

import com.mongodb.casbah.Imports._

import scala.collection.immutable

class CasbahJournal extends SyncWriteJournal with CasbahRecovery with CasbahHelper with ActorLogging {
  val config = context.system.settings.config.getConfig("casbah-journal")

  val mongoUrl = config.getString("mongo-url")

  val serialization = SerializationExtension(context.system)

  val uri = MongoClientURI(mongoUrl)
  val client =  MongoClient(uri)
  val db = client(uri.database.get)
  val collection = db(uri.collection.get)

  collection.ensureIndex(idx1, idx1Options)
  collection.ensureIndex(idx2)
  collection.ensureIndex(idx3)

  def msgToBytes(p: PersistentRepr): Array[Byte] = serialization.serialize(p).get
  def msgFromBytes(a: Array[Byte]) = serialization.deserialize(a, classOf[PersistentRepr]).get

  def writeMessages(persistentBatch: immutable.Seq[PersistentRepr]): Unit = {
    implicit val concern = WriteConcern.Safe

    val batch = persistentBatch.map(pr => writeJSON(pr.processorId, pr.sequenceNr, msgToBytes(pr)))
    collection.insert(batch:_ *)
  }

  def writeConfirmations(confirmations: immutable.Seq[akka.persistence.PersistentConfirmation]): Unit = {
    implicit val concern = WriteConcern.Safe

    val batch = confirmations map { c => confirmJSON(c.processorId, c.sequenceNr, c.channelId) }
    collection.insert(batch:_ *)
  }

  def deleteMessages(messageIds: immutable.Seq[akka.persistence.PersistentId], permanent: Boolean): Unit = {
    implicit val concern = WriteConcern.Safe

    if (permanent) {
      val batch = messageIds map { mid => delStatement(mid.processorId, mid.sequenceNr) }
      collection.remove(delOrStatement(batch.toList), concern)
    } else {
      val batch = messageIds map { mid => deleteMarkJSON(mid.processorId, mid.sequenceNr) }
      collection.insert(batch:_ *)
    }
  }

  def deleteMessagesTo(processorId: String, toSequenceNr: Long, permanent: Boolean): Unit = {
    implicit val concern = WriteConcern.Safe

    if (permanent)
      collection.remove(delToStatement(processorId, toSequenceNr), concern)
    else {
      val msgs = collection.find(delToStatement(processorId, toSequenceNr)).sort(minSnrSortStatement).toList
      val deletedMsgs = msgs.filter(_.get(MarkerKey).asInstanceOf[String] == MarkerDelete)
      val msgsToDelete = msgs.filterNot(msg =>
        deletedMsgs.exists(_.get(SequenceNrKey).asInstanceOf[Long] == msg.get(SequenceNrKey).asInstanceOf[Long]))
      // val msgsToDelete = for {
      //   msg <- msgs
      //   if deletedMsgs.find(_.get(SequenceNrKey).asInstanceOf[Long] == msg.get(SequenceNrKey).asInstanceOf[Long]) == None
      // } yield msg
      val batch = msgsToDelete map { msg => deleteMarkJSON(processorId, msg.get(SequenceNrKey).asInstanceOf[Long]) }
      collection.insert(batch:_ *)
    }
  }

  override def postStop(): Unit = {
    client.close()
  }
}
