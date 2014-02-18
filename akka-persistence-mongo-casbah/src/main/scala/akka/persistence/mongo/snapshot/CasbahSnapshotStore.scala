/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.mongo.snapshot

import akka.actor.ActorLogging
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SnapshotMetadata, SelectedSnapshot, SnapshotSelectionCriteria}
import scala.concurrent.Future

private[persistence] class CasbahSnapshotStore  extends SnapshotStore with ActorLogging {

  override def delete(processorId: String, criteria: SnapshotSelectionCriteria): Unit = ???

  override def delete(metadata: SnapshotMetadata): Unit = ???

  override def saved(metadata: SnapshotMetadata): Unit = ???

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = ???

  override def loadAsync(processorId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = ???
}
