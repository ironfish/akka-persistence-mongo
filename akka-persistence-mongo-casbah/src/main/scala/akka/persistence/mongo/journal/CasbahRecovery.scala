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

  def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback:
      PersistentRepr ⇒ Unit): Future[Unit] = Future {
    replay(persistenceId, fromSequenceNr, toSequenceNr, max)(replayCallback)
  }

  private def replay(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback:
      PersistentRepr => Unit): Unit = {

    import com.mongodb.casbah.Implicits._

    val coll = collection.find(replayFindStatement(persistenceId, fromSequenceNr, toSequenceNr)).sort(recoverySortStatement)
    val collSorted = immutable.SortedMap(coll.toList.zipWithIndex.groupBy(x => x._1.as[Long](SequenceNrKey)).toSeq: _*)
    collSorted.flatMap { x =>
      if (x._2.headOption.get._2 < max) {
        val entry = x._2.find(_._1.get(MarkerKey) == MarkerAccepted).map(_._1.as[Array[Byte]](MessageKey)).map(fromBytes[PersistentRepr])
        val deleted = x._2.exists(_._1.get(MarkerKey) == MarkerDelete)
        val confirms = x._2.filter(_._1.as[String](MarkerKey).substring(0,1) == MarkerConfirmPrefix).map(_._1.as[String](MarkerKey).substring(2)).to[immutable.Seq]
        if (entry.nonEmpty) Some(entry.get.update(deleted = deleted, confirms = confirms)) else None
      } else None
    } map replayCallback
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = Future {
    val cursor = collection.find(snrQueryStatement(persistenceId)).sort(maxSnrSortStatement).limit(1)
    if (cursor.hasNext) cursor.next().getAs[Long](SequenceNrKey).get else 0L
  }
}
