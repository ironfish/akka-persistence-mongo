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

class CasbahJournal extends SyncWriteJournal with CasbahReplay with CasbahHelper with ActorLogging {
  val config = context.system.settings.config.getConfig("casbah-journal")

  val mongoUrl = config.getString("mongo-url")

  val serialization = SerializationExtension(context.system)

  val uri = MongoClientURI(mongoUrl)
  val client =  MongoClient(uri)
  val db = client(uri.database.get)
  val collection = db(uri.collection.get)

  def msgToBytes(p: PersistentRepr): Array[Byte] = serialization.serialize(p).get
  def msgFromBytes(a: Array[Byte]) = serialization.deserialize(a, classOf[PersistentRepr]).get

  /**
   * Plugin API: synchronously writes a batch of persistent messages to the journal.
   * The batch write must be atomic i.e. either all persistent messages in the batch
   * are written or none.
   */
  def write(persistentBatch: immutable.Seq[PersistentRepr]): Unit = {
    persistentBatch.foreach { p ⇒
      collection.insert(writeJSON(p.processorId, p.sequenceNr, msgToBytes(p)), WriteConcern.Safe)
    }
  }

  /**
   * Plugin API: synchronously deletes a persistent message. If `physical` is set to
   * `false`, the persistent message is marked as deleted, otherwise it is physically
   * deleted.
   */
  def delete(processorId: String, fromSequenceNr: Long, toSequenceNr: Long, permanent: Boolean): Unit =
    fromSequenceNr to toSequenceNr foreach { sequenceNr =>
      if (permanent) collection.remove(deleteQueryStatement(processorId, sequenceNr), WriteConcern.Safe)
      else collection.insert(deleteMarkJSON(processorId, sequenceNr), WriteConcern.Safe)
    }

  /**
   * Plugin API: synchronously writes a delivery confirmation to the journal.
   */
  def confirm(processorId: String, sequenceNr: Long, channelId: String): Unit = {
    collection.insert(confirmJSON(processorId, sequenceNr, channelId), WriteConcern.Safe)
  }

  override def postStop(): Unit = {
    client.close()
  }
}
