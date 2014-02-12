/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.journal.mongo

import akka.persistence._
import akka.persistence.journal.AsyncRecovery

import com.mongodb.casbah.Imports._

import scala.collection.immutable
import scala.concurrent._

trait CasbahRecovery extends AsyncRecovery { this: CasbahJournal ⇒

  implicit lazy val replayDispatcher = context.system.dispatchers.lookup(config.getString("replay-dispatcher"))

  def asyncReplayMessages(processorId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback:
      PersistentRepr ⇒ Unit): Future[Unit] = Future {
    replay(processorId, fromSequenceNr, toSequenceNr, max)(replayCallback)
  }

  private def replay(processorId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback:
      PersistentRepr => Unit): Unit = {

    import com.mongodb.casbah.Implicits._

    val dcs = collection.aggregate(
      matchStatement(processorId, fromSequenceNr, toSequenceNr),
      groupStatement,
      sortStatement)

    val dcsItr = dcs.results.iterator

    @scala.annotation.tailrec
    def go(dcsItr: Iterator[DBObject], maxAcc: Long) {
      if (dcsItr.hasNext && maxAcc < max) {
        val dc = dcsItr.next()
        val details = dc.get(AddDetailsKey).asInstanceOf[DBObject]

        val jsonChannels =
          details.filter(_._2.asInstanceOf[DBObject].get(MarkerKey).asInstanceOf[String].substring(0,1) == MarkerConfirmPrefix)

        val channels: Seq[String] =
          jsonChannels.map(_._2.asInstanceOf[DBObject].get(MarkerKey).asInstanceOf[String].substring(2)).toSeq

        val jsonDeleted = details.filter(_._2.asInstanceOf[DBObject].get(MarkerKey).asInstanceOf[String] == MarkerDelete)
        val deleted: Seq[String] = jsonDeleted.map(_._2.asInstanceOf[DBObject].get(MarkerKey).asInstanceOf[String]).toSeq

        val jsonAccepted = details.filter(_._2.asInstanceOf[DBObject].get(MarkerKey).asInstanceOf[String] == MarkerAccepted)

        val message: Seq[Array[Byte]] =
          jsonAccepted.map(_._2.asInstanceOf[DBObject].get(MessageKey).asInstanceOf[Array[Byte]]).toSeq

        if (!message.isEmpty) { // might have orphan deletes/confirms
          val messageOut = msgFromBytes(message.head).update(confirms = channels.to[immutable.Seq],
            deleted = {if (deleted.isEmpty) false else true})
          replayCallback(messageOut)
        }

        go(dcsItr, maxAcc + 1L)
      }
    }
    go(dcsItr, 0L)
  }

  override def asyncReadHighestSequenceNr(processorId: String, fromSequenceNr: Long): scala.concurrent.Future[Long] = future {
    val cursor = collection.find(snrQueryStatement(processorId)).sort(maxSnrSortStatement).limit(1)
    if (cursor.hasNext) cursor.next().getAs[Long](SequenceNrKey).get else 0L
  }
}
