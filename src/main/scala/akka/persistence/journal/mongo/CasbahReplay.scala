/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.journal.mongo

import akka.persistence._
import akka.persistence.journal.AsyncReplay

import com.mongodb.casbah.Imports._

import scala.collection.immutable
import scala.concurrent._

trait CasbahReplay extends AsyncReplay { this: CasbahJournal ⇒

  implicit lazy val replayDispatcher = context.system.dispatchers.lookup(config.getString("replay-dispatcher"))

  def replayAsync(processorId: String, fromSequenceNr: Long, toSequenceNr: Long)(replayCallback: PersistentRepr ⇒
    Unit): Future[Long] = Future {

    replay(processorId, fromSequenceNr, toSequenceNr)(replayCallback)
  }

  private def replay(processorId: String, fromSequenceNr: Long, toSequenceNr: Long)(replayCallback: PersistentRepr =>
    Unit): Long = {

    import com.mongodb.casbah.Implicits._

    val dcs = collection.aggregate(
      matchStatement(processorId, fromSequenceNr, toSequenceNr),
      groupStatement,
      sortStatement)

    val dcsItr = dcs.results.iterator

    @scala.annotation.tailrec
    def go(dcsItr: Iterator[DBObject]) {
      if (dcsItr.hasNext) {
        val dc = dcsItr.next()
        val details = dc.get(AddDetailsKey).asInstanceOf[DBObject]

        val jsonChannels = details.filter(_._2.asInstanceOf[DBObject].get(MarkerKey).asInstanceOf[String].substring(0,1) == MarkerConfirmPrefix)
        val channels: Seq[String] = jsonChannels.map(_._2.asInstanceOf[DBObject].get(MarkerKey).asInstanceOf[String].substring(2)).toSeq

        val jsonDeleted = details.filter(_._2.asInstanceOf[DBObject].get(MarkerKey).asInstanceOf[String] == MarkerDelete)
        val deleted: Seq[String] = jsonDeleted.map(_._2.asInstanceOf[DBObject].get(MarkerKey).asInstanceOf[String]).toSeq

        val jsonAccepted = details.filter(_._2.asInstanceOf[DBObject].get(MarkerKey).asInstanceOf[String] == MarkerAccepted)
        val message: Seq[Array[Byte]] = jsonAccepted.map(_._2.asInstanceOf[DBObject].get(MessageKey).asInstanceOf[Array[Byte]]).toSeq

        // TODO what is there is no legitimate message? (i.e. Array.empty[Byte])
        val messageOut = msgFromBytes(message.head).update(confirms = channels.to[immutable.Seq], deleted = {if (deleted.isEmpty) false else true})
        replayCallback(messageOut)

        go(dcsItr)
      }
    }
    go(dcsItr)
    maxSnr(processorId)
  }

  private def maxSnr(processorId: String): Long = {
    val cursor = collection.find(maxSnrQueryStatement(processorId)).sort(maxSnrSortStatement).limit(1)
    if (cursor.hasNext) cursor.next().getAs[Long](SequenceNrKey).get else 0L
  }
}
