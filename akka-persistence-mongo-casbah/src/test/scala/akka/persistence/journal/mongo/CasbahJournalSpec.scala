/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.journal.mongo

import akka.actor._
import akka.persistence.journal.JournalSpec
import akka.testkit._

import com.typesafe.config.ConfigFactory

object CasbahJournalSpec {
  def config(port: Int) = ConfigFactory.parseString(
    s"""
      |akka.persistence.journal.plugin = "casbah-journal"
      |akka.persistence.snapshot-store.local.dir = "target/snapshots"
      |casbah-journal.mongo-url = "mongodb://localhost:$port/store.messages"
      |casbah-journal.mongo-journal-write-concern = "acknowledged"
    """.stripMargin).withFallback(JournalSpec.config)
}

import CasbahJournalSpec._
import PortServer._

class CasbahJournalSpec extends TestKit(ActorSystem("test", config(freePort)))
    with JournalSpec
    with MongoCleanup
