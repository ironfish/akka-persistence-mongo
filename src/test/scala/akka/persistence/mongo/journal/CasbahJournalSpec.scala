/**
  *  Copyright (C) 2015-2016 Duncan DeVore. <https://github.com/ironfish/>
  */
package akka.persistence.mongo.journal

import akka.persistence.CapabilityFlag
import akka.persistence.journal.JournalSpec
import akka.persistence.mongo.{CasbahJournalCommon, EmbeddedMongoSupport}
import akka.persistence.mongo.PortServer._

import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object CasbahJournalSpec extends CasbahJournalCommon {
  override protected val rejectNonSerializableObjectsKey: String = s"$configRootKey.reject-non-serializable-objects"
  override protected val mongoUrlKey: String = s"$configRootKey.mongo-url"

  val config = ConfigFactory.parseString(
    s"""
       |akka.persistence.journal.plugin = "$configRootKey"
       |$mongoUrlKey = "mongodb://localhost:$freePort/store.messages"
       |$rejectNonSerializableObjectsKey = true
    """.stripMargin)
}

class CasbahJournalSpec extends JournalSpec(CasbahJournalSpec.config)
  with EmbeddedMongoSupport {

  import CasbahJournalSpec._

  override def supportsRejectingNonSerializableObjects: CapabilityFlag = rejectNonSerializableObjectId

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
}
