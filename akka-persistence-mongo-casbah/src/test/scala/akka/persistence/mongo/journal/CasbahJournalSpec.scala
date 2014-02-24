/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.mongo.journal

import akka.actor._
import akka.persistence.mongo.JournalSpec
import akka.persistence.mongo.MongoCleanup
import akka.testkit._

import com.typesafe.config.ConfigFactory

object CasbahJournalSpec {
  def config(port: Int) = ConfigFactory.parseString(
    s"""
      |akka.persistence.journal.plugin = "casbah-journal"
      |akka.persistence.snapshot-store.plugin = "casbah-snapshot-store"
      |casbah-journal.mongo-journal-url = "mongodb://localhost:$port/store.messages"
      |casbah-journal.mongo-journal-write-concern = "acknowledged"
      |casbah-journal.mongo-journal-write-concern-timeout = 10000
      |casbah-snapshot-store.mongo-snapshot-url = "mongodb://localhost:$port/store.snapshots"
      |casbah-snapshot-store.mongo-snapshot-write-concern = "acknowledged"
      |casbah-snapshot-store.mongo-snapshot-write-concern-timeout = 10000
    """.stripMargin).withFallback(JournalSpec.config)
}//       |akka.persistence.snapshot-store.local.dir = "target/snapshots"

import CasbahJournalSpec._
import akka.persistence.mongo.PortServer._

class CasbahJournalSpec extends TestKit(ActorSystem("test", config(freePort)))
    with JournalSpec
    with MongoCleanup {

  override val actorSystem = system
}
