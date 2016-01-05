/**
  *  Copyright (C) 2015-2016 Duncan DeVore. <https://github.com/ironfish/>
  */
package akka.persistence.mongo.journal

import akka.persistence._
import akka.persistence.journal.AsyncRecovery

import scala.concurrent._

trait CasbahRecovery extends AsyncRecovery { this: CasbahJournal ⇒

  import CasbahJournalRoot._

  implicit lazy val replayDispatcher = context.system.dispatchers.lookup(replayDispatcherId)

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] =
    Future (highestSequenceNrExecute(mongoCollection, persistenceId))

  def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)
    (recoveryCallback: PersistentRepr ⇒ Unit): Future[Unit] =  Future {

    val maxNbrOfMessages: Int =
      if (max <= Int.MaxValue) max.toInt
      else Int.MaxValue

    if (maxNbrOfMessages > 0)
      replayCursor(mongoCollection, persistenceId, fromSequenceNr, toSequenceNr, maxNbrOfMessages).foreach(recoveryCallback)
  }
}
