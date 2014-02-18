/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.mongo

import akka.actor.ActorSystem
import akka.serialization.SerializationExtension

import com.typesafe.config.Config

import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.reflect.classTag


private[mongo] object MongoPersistenceRoot {

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

import MongoPersistenceRoot._

private[mongo] trait MongoPersistenceRoot {
  val actorSystem: ActorSystem
  lazy val serialization = SerializationExtension(actorSystem)
  def toBytes(data: AnyRef): Array[Byte] = serialization.serialize(data).get
  def fromBytes[T: ClassTag](a: Array[Byte]): T =
    serialization.deserialize(a, classTag[T].runtimeClass.asInstanceOf[Class[T]]).get
}

private[mongo] trait MongoPersistenceJournalRoot extends MongoPersistenceRoot {
  def configJournal: Config
//  def configJournal: Config = actorSystem.settings.config.getConfig("casbah-journal")
  def configReplayDispatcher = configJournal.getString("replay-dispatcher")
  def configMongoUrl = configJournal.getString("mongo-url")
  def configMongoJournalWriteConcern: MongoWriteConcern = configJournal.getString("mongo-journal-write-concern")
}

private[mongo] trait MongoPersistenceSnapshotRoot extends MongoPersistenceRoot {
  def configSnapshot: Config
  def configSnapshotLocalDir = configSnapshot.getString("local.dir")
}
