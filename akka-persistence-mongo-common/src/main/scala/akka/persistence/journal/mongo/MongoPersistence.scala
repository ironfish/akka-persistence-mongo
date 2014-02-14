/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.journal.mongo

import com.typesafe.config.Config

private[this] object MongoPersistenceRoot {

  sealed trait MongoWriteConcern
  case object Acknowledged extends MongoWriteConcern
  case object Journaled extends MongoWriteConcern
  case object ReplicasAcknowledged extends MongoWriteConcern

  implicit def configWriteConcern(concern: String): MongoWriteConcern = concern match {
    case "acknowledged"          => Acknowledged
    case "journaled"             => Journaled
    case "replicas-acknowledged" => ReplicasAcknowledged
  }
}

trait MongoPersistenceJournalRoot {
  import MongoPersistenceRoot._

  private[mongo] def configJournal: Config
  private[mongo] def configReplayDispatcher = configJournal.getString("replay-dispatcher")
  private[mongo] def configMongoUrl = configJournal.getString("mongo-url")
  private[mongo] def configMongoWriteConcern: MongoWriteConcern = configJournal.getString("mongo-journal-write-concern")
}

trait MongoPersistenceSnapshotRoot {
  private[mongo] def configSnapshot: Config
  private[mongo] def configSnapshotLocalDir = configSnapshot.getString("local.dir")
}
