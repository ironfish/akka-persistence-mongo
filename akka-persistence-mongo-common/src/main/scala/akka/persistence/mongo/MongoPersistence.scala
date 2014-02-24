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

  sealed trait MongoWriteConcern { def timeout: Int }
  case class Acknowledged(timeout: Int) extends MongoWriteConcern
  case class Journaled(timeout: Int) extends MongoWriteConcern
  case class ReplicasAcknowledged(timeout: Int) extends MongoWriteConcern

  implicit def configWriteConcern(wc: (String, Int)): MongoWriteConcern = wc._1 match {
    case "acknowledged"          => Acknowledged(wc._2)
    case "journaled"             => Journaled(wc._2)
    case "replicas-acknowledged" => ReplicasAcknowledged(wc._2)
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
  def configMongoJournalUrl = configJournal.getString("mongo-journal-url")
  def configMongoJournalWriteConcern: MongoWriteConcern =
    (configJournal.getString("mongo-journal-write-concern"),
      configJournal.getInt("mongo-journal-write-concern-timeout"))
}

private[mongo] trait MongoPersistenceSnapshotRoot extends MongoPersistenceRoot {
  def configSnapshot: Config
  def configMongoSnapshotUrl = configSnapshot.getString("mongo-snapshot-url")
  def configSnapshotLocalDir = configSnapshot.getString("local.dir")
  def configMongoSnapshotWriteConcern: MongoWriteConcern =
    (configSnapshot.getString("mongo-snapshot-write-concern"),
      configSnapshot.getInt("mongo-snapshot-write-concern-timeout"))
  def configMongoSnapshotLoadAttempts = configSnapshot.getInt("mongo-snapshot-load-attempts")
}
