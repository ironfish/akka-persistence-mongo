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
        val details = dc.as[MongoDBList](AddDetailsKey).map(_.asInstanceOf[DBObject])

        val confirms = details.filter(_.as[String](MarkerKey).substring(0,1) == MarkerConfirmPrefix)
          .map(_.as[String](MarkerKey).substring(2)).to[immutable.Seq]

        val deleted = details.exists(_.get(MarkerKey) == MarkerDelete)

        val message = details.find(_.get(MarkerKey) == MarkerAccepted).map(_.as[Array[Byte]](MessageKey))
          .map(fromBytes[PersistentRepr])

        if (message.nonEmpty) replayCallback(message.get.update(deleted = deleted, confirms = confirms))

        go(dcsItr, maxAcc + 1L)
      }
    }
    go(dcsItr, 0L)
  }

  override def asyncReadHighestSequenceNr(processorId: String, fromSequenceNr: Long): Future[Long] = future {
    val cursor = collection.find(snrQueryStatement(processorId)).sort(maxSnrSortStatement).limit(1)
    if (cursor.hasNext) cursor.next().getAs[Long](SequenceNrKey).get else 0L
  }
}
