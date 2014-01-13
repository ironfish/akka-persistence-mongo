/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.journal.mongo

import akka.testkit.TestKit

import com.mongodb.casbah.Imports._

import java.io.File

import org.apache.commons.io.FileUtils

import org.scalatest.{Suite, BeforeAndAfterAll}

trait MongoCleanup extends BeforeAndAfterAll { this: TestKit with Suite =>

  val journalConfig = system.settings.config.getConfig("casbah-journal")
  val snapshotConfig = system.settings.config.getConfig("akka.persistence.snapshot-store.local")

  override protected def afterAll(): Unit = {
    val mongoUrl = journalConfig.getString("mongo-url")
    val uri = MongoClientURI(mongoUrl)
    val client =  MongoClient(uri)
    val db = client(uri.database.get)
    val coll = db(uri.collection.get)
    coll.drop()
    FileUtils.deleteDirectory(new File(snapshotConfig.getString("dir")))
    client.close()
    system.shutdown()
  }
}