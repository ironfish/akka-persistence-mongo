/**
  *  Copyright (C) 2015-2016 Duncan DeVore. <https://github.com/ironfish/>
  */
package akka.persistence.mongo.journal

import akka.actor.{ActorSystem, ActorLogging}
import akka.persistence._
import akka.persistence.journal.AsyncWriteJournal

import com.mongodb.casbah.Imports._
import com.typesafe.config.Config

import scala.collection.immutable
import scala.concurrent.{Promise, Future}
import scala.util.{Failure, Success, Try}

private[journal] class CasbahJournal extends AsyncWriteJournal
  with CasbahJournalRoot
  with CasbahRecovery
  with ActorLogging {

  import CasbahJournalRoot._

  override val actorSystem: ActorSystem = context.system

  override val config: Config = context.system.settings.config.getConfig(configRootKey)

  implicit val concern: WriteConcern = writeConcern

  implicit val rejectNonSerializableObjects: Boolean = rejectNonSerializableObjectId

  initialize()

  def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {

    val messagesToTryAndPersist: immutable.Seq[Try[DBObject]] = messages.flatMap(message =>
      message.payload.map(persistentReprToDBObject))

    val persistedMessages: Future[WriteResult] =
      Future(persistExecute(mongoCollection, messagesToTryAndPersist.flatMap(_.toOption)))

    val promise = Promise[immutable.Seq[Try[Unit]]]()

    persistedMessages.onComplete {
      case Success(_) if messagesToTryAndPersist.exists(_.isFailure)  =>
        promise.success(messagesToTryAndPersist.map(_ match {
          case Success(_) => Success((): Unit)
          case Failure(error) => Failure(error)
        }))
      case Success(_) =>
        promise.complete(Success(Nil))
      case Failure(e) => promise.failure(e)
    }
    promise.future
  }

  def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    Future(deleteTo(mongoCollection, concern, persistenceId, toSequenceNr))
  }

  override def postStop(): Unit = {
    shutdown()
  }
}
