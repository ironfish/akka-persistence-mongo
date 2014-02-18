/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.mongo.journal

import akka.persistence._
import akka.persistence.journal.AsyncRecovery

import com.mongodb.casbah.Imports._

import scala.collection.immutable
import scala.concurrent._

trait CasbahRecovery extends AsyncRecovery { this: CasbahJournal ⇒

  implicit lazy val replayDispatcher = context.system.dispatchers.lookup(configReplayDispatcher)

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
        val details = dc.get(AddDetailsKey).asInstanceOf[DBObject].map(_._2.asInstanceOf[DBObject])

        val channels = details.filter(_.get(MarkerKey).toString.substring(0,1) == MarkerConfirmPrefix)
          .map(_.get(MarkerKey).toString.substring(2)).to[immutable.Seq]

        val deleted = details.exists(_.get(MarkerKey) == MarkerDelete)

        val message = details.find(_.get(MarkerKey) == MarkerAccepted).map(_.get(MessageKey)
          .asInstanceOf[Array[Byte]]).map(fromBytes[PersistentRepr])

        // might have orphan deletes/confirms
        if (!message.isEmpty) replayCallback(message.get.update(deleted = deleted, confirms = channels))

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
