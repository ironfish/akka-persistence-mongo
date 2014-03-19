/**
  *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
  */
package akka.persistence.mongo.journal

import akka.actor._
import akka.persistence._
import akka.persistence.mongo.MongoCleanup
import akka.testkit._

import com.typesafe.config.ConfigFactory

import org.scalatest._

import scala.concurrent.duration._

object CasbahBigSpec {

   def config(port: Int) = ConfigFactory.parseString(
     s"""
       |akka.persistence.journal.plugin = "casbah-journal"
       |akka.persistence.snapshot-store.plugin = "casbah-snapshot-store"
       |akka.persistence.journal.max-deletion-batch-size = 3
       |akka.persistence.publish-plugin-commands = on
       |akka.persistence.publish-confirmations = on
       |casbah-journal.mongo-journal-url = "mongodb://localhost:$port/store.messages"
       |casbah-journal.mongo-journal-write-concern = "acknowledged"
       |casbah-journal.mongo-journal-write-concern-timeout = 10000
       |casbah-snapshot-store.mongo-snapshot-url = "mongodb://localhost:$port/store.snapshots"
       |casbah-snapshot-store.mongo-snapshot-write-concern = "acknowledged"
       |casbah-snapshot-store.mongo-snapshot-write-concern-timeout = 10000
     """.stripMargin)

   case class Delete(snr: Long, permanent: Boolean)
   case class DeleteTo(snr: Long, permanent: Boolean)

   class ProcessorA(override val processorId: String) extends Processor {
     def receive = {
       case Persistent(payload, sequenceNr) =>
         sender ! payload
         sender ! sequenceNr
         sender ! recoveryRunning
       case Delete(sequenceNr, permanent) =>
         deleteMessage(sequenceNr, permanent)
       case DeleteTo(sequenceNr, permanent) =>
         deleteMessages(sequenceNr, permanent)
     }
   }
}

import CasbahBigSpec._
import akka.persistence.mongo.PortServer._

class CasbahBigSpec extends TestKit(ActorSystem("test", config(freePort)))
     with ImplicitSender
     with WordSpecLike
     with Matchers
     with MongoCleanup {

   override val actorSystem = system

   "A Casbah journal" should {

     "write and replay 100K+ messages" ignore {
       val processor1 = system.actorOf(Props(classOf[ProcessorA], "p1"))
       1L to 160000L foreach { i =>
         processor1 ! Persistent(s"a-$i")
         expectMsgAllOf(3.seconds, s"a-$i", i, false)
       }

       val processor2 = system.actorOf(Props(classOf[ProcessorA], "p1"))
       1L to 160000L foreach { i =>
         expectMsgAllOf(s"a-$i", i, true)
       }

       processor2 ! Persistent("b")
       expectMsgAllOf("b", 160001L, false)
     }
   }
}
