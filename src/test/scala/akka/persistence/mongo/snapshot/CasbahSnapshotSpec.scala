/**
  *  Copyright (C) 2015-2016 Duncan DeVore. <https://github.com/ironfish/>
  */
package akka.persistence.mongo.snapshot

import akka.persistence.SnapshotProtocol.{LoadSnapshotResult, LoadSnapshot}
import akka.persistence.SnapshotSelectionCriteria
import akka.persistence.mongo.{CasbahCommon, CasbahSnapshotCommon, EmbeddedMongoSupport}
import akka.persistence.mongo.PortServer._
import akka.persistence.snapshot.SnapshotStoreSpec
import akka.testkit.TestProbe
import com.mongodb.casbah.Imports._
import com.typesafe.config.ConfigFactory

import scala.compat.Platform
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object CasbahSnapshotSpec extends CasbahSnapshotCommon {
  override protected val mongoUrlKey: String = s"$configRootKey.mongo-url"

  val config = ConfigFactory.parseString(
    s"""
       |akka.persistence.snapshot-store.plugin = "$configRootKey"
       |$mongoUrlKey = "mongodb://localhost:$freePort/store.snapshots"
    """.stripMargin)
}

class CasbahSnapshotSpec extends SnapshotStoreSpec(CasbahSnapshotSpec.config)
  with EmbeddedMongoSupport {

  implicit val concern: WriteConcern = WriteConcern.Journaled

  import CasbahCommon._
  import CasbahSnapshotSpec._

  override def beforeAll(): Unit = {
    embeddedMongoStartup()
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    mongoDB.dropDatabase()
    client.close()
    system.terminate()
    Await.result(system.whenTerminated, Duration.Inf)
    embeddedMongoShutdown()
  }

  private def badSnapshot(persistenceId: String, sequenceNr: Long): DBObject = {
    val builder = MongoDBObject.newBuilder
      builder += persistenceIdKey -> persistenceId
      builder += sequenceNrKey -> sequenceNr
      builder += snapshotKey -> s"BAD SNAPSHOT $persistenceId"
      builder += timestampKey -> Platform.currentTime
      builder.result()
  }

  "A Casbah snapshot store" must {
    "make up to 3 snapshot loading attempts" in {
      val probe = TestProbe()

      // load most recent snapshot
      snapshotStore.tell(LoadSnapshot(pid, SnapshotSelectionCriteria.Latest, Long.MaxValue), probe.ref)

      // get most recent snapshot
      val expected = probe.expectMsgPF() { case LoadSnapshotResult(Some(snapshot), _) => snapshot }

      mongoCollection.insert(badSnapshot(pid, 123L))(identity, concern)
      mongoCollection.insert(badSnapshot(pid, 124L))(identity, concern)

      // load most recent snapshot, first two attempts will fail ...
      snapshotStore.tell(LoadSnapshot(pid, SnapshotSelectionCriteria.Latest, Long.MaxValue), probe.ref)

      // third attempt succeeds
      probe.expectMsg(LoadSnapshotResult(Some(expected), Long.MaxValue))
    }
    "give up after 3 snapshot loading attempts" in {
      val probe = TestProbe()

      // load most recent snapshot
      snapshotStore.tell(LoadSnapshot(pid, SnapshotSelectionCriteria.Latest, Long.MaxValue), probe.ref)

      // wait for most recent snapshot
      probe.expectMsgPF() { case LoadSnapshotResult(Some(snapshot), _) => snapshot }

      // write three more snapshots that cannot be de-serialized.
      mongoCollection.insert(badSnapshot(pid, 123L))(identity, concern)
      mongoCollection.insert(badSnapshot(pid, 124L))(identity, concern)
      mongoCollection.insert(badSnapshot(pid, 125L))(identity, concern)

      // load most recent snapshot, first three attempts will fail ...
      snapshotStore.tell(LoadSnapshot(pid, SnapshotSelectionCriteria.Latest, Long.MaxValue), probe.ref)

      // no 4th attempt has been made
      probe.expectMsg(LoadSnapshotResult(None, Long.MaxValue))
    }
  }
}