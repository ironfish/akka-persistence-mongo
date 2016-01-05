/**
  *  Copyright (C) 2015-2016 Duncan DeVore. <https://github.com/ironfish/>
  */
package akka.persistence.mongo.snapshot

import akka.actor.{ActorSystem, ActorLogging}
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SnapshotMetadata, SelectedSnapshot, SnapshotSelectionCriteria}

import com.mongodb.casbah.Imports._

import com.typesafe.config.Config

import scala.collection.immutable
import scala.concurrent._

private[snapshot] class CasbahSnapshot extends SnapshotStore
  with CasbahSnapshotRoot
  with ActorLogging {

  import context.dispatcher

  override val actorSystem: ActorSystem = context.system

  override val config: Config = context.system.settings.config.getConfig(configRootKey)

  implicit val concern: WriteConcern = writeConcern

  initialize()

  def deleteAsync(metadata: SnapshotMetadata): Future[Unit] =
    Future( mongoCollection.remove(deleteStatement(metadata)))

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] =
    Future(mongoCollection.remove(deleteStatement(persistenceId, criteria)))

  // Select the youngest of {n} snapshots that match the upper bound. This helps where a snapshot may not have
  // persisted correctly because of a JVM crash. As a result an attempt to load the snapshot may fail but an older
  // may succeed.
  override def loadAsync(persistenceId: String,
    criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = Future {

    val snapshots: MongoCursor =
      mongoCollection.find(loadStatement(persistenceId, criteria)).sort(sortStatement).limit(loadAttempts)

    snapshots.flatMap(dbObjectToSelectedSnapshot).to[immutable.Seq].headOption
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] =
    Future(mongoCollection.insert(snapshotToDbObject(metadata, snapshot)))

  override def postStop(): Unit = {
    shutdown()
  }
}
