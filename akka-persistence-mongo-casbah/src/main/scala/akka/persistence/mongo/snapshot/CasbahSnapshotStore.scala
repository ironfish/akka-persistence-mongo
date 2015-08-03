/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <https://github.com/ironfish/>
 */
package akka.persistence.mongo.snapshot

import akka.actor.{ActorSystem, ActorLogging}
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SnapshotMetadata, SelectedSnapshot, SnapshotSelectionCriteria}

import com.mongodb.casbah.Imports._

import com.typesafe.config.Config

import scala.collection.immutable
import scala.concurrent._
import scala.util.{Failure, Success}

private[persistence] class CasbahSnapshotStore  extends SnapshotStore
    with CasbahSnapshotHelper
    with ActorLogging {

  import context.dispatcher

  override val actorSystem: ActorSystem = context.system

  implicit val concern = casbahSnapshotWriteConcern

  // TODO move to MongoPersistence.scala???
  override def configSnapshot: Config = context.system.settings.config.getConfig("casbah-snapshot-store")

  /** Snapshots we're in progress of saving */
  private var saving = immutable.Set.empty[SnapshotMetadata]

  override def delete(persistenceId: String, criteria: SnapshotSelectionCriteria): Unit =
    collection.remove(delStatement(persistenceId, criteria))

  override def delete(metadata: SnapshotMetadata): Unit = {
    saving -= metadata
    collection.remove(delStatement(metadata))
  }

  override def saved(metadata: SnapshotMetadata): Unit =
    saving -= metadata

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    saving += metadata
    Future(collection.insert(writeJSON(metadata, snapshot)))
  }

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    // Select the youngest of {n} snapshots that match the upper bound. This helps where a snapshot may not have
    // persisted correctly because of a JVM crash. As a result an attempt to load the snapshot may fail but an older
    // may succeed.
    Future {
      val snaps = collection.find(snapshotsQueryStatement(persistenceId, criteria))
        .sort(snapshotsSortStatement)
        .limit(configMongoSnapshotLoadAttempts)
      load(snaps.to[immutable.Seq])
    }
  }

  @scala.annotation.tailrec
  private def load(snaps: immutable.Seq[DBObject]): Option[SelectedSnapshot] = snaps.headOption match {
    case None => None
    case Some(snap) =>
      readJSON(snap) match {
        case Success(s) => Some(s)
        case Failure(e) =>
          log.error(e, s"error loading snapshot $snap")
          load(snaps.tail)
      }
  }
}
