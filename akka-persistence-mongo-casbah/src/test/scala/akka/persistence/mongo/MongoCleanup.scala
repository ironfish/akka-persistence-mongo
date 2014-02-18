/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.mongo

import akka.testkit.TestKit

import com.mongodb.casbah.Imports._

import java.io.File

import org.apache.commons.io.FileUtils

import org.scalatest.{Suite, BeforeAndAfterAll}

private[mongo] trait MongoCleanup extends EmbeddedMongoSupport
    with BeforeAndAfterAll
    with MongoPersistenceJournalRoot
    with MongoPersistenceSnapshotRoot { this: TestKit with Suite =>

  override def configJournal = system.settings.config.getConfig("casbah-journal")
  override def configSnapshot = system.settings.config.getConfig("akka.persistence.snapshot-store")

  override def beforeAll(): Unit = {
    embeddedMongoStartup()
  }

  override def afterAll(): Unit = {
    val uri = MongoClientURI(configMongoUrl)
    val client = MongoClient(uri)
    val db = client(uri.database.get)
    db.dropDatabase()
    FileUtils.deleteDirectory(new File(configSnapshotLocalDir))
    client.close()
    system.shutdown()
    system.awaitTermination()
    embeddedMongoShutdown()
  }
}
