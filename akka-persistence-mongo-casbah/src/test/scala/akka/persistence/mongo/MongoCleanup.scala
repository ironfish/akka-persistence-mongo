/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.mongo

import akka.testkit.TestKitBase

import com.mongodb.casbah.Imports._

import org.scalatest.{Suite, BeforeAndAfterAll}

private[mongo] trait MongoCleanup extends EmbeddedMongoSupport
    with BeforeAndAfterAll
    with MongoPersistenceJournalRoot
    with MongoPersistenceSnapshotRoot { this: TestKitBase with Suite =>

  override def configJournal = system.settings.config.getConfig("casbah-journal")
  override def configSnapshot = system.settings.config.getConfig("casbah-snapshot-store")

  override def beforeAll(): Unit = {
    embeddedMongoStartup()
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    val uri = MongoClientURI(configMongoJournalUrl)
    val client = MongoClient(uri)
    val db = client(uri.database.get)
    db.dropDatabase()
    client.close()
    system.shutdown()
    system.awaitTermination()
    embeddedMongoShutdown()
  }
}
