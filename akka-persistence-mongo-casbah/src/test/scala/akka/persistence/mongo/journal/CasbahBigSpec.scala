/**
  *  Copyright (C) 2013-2014 Duncan DeVore. <https://github.com/ironfish/>
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

  class ProcessorA(val persistenceId: String) extends PersistentActor {
      def receiveRecover: Receive = handle

      def receiveCommand: Receive = {
        case payload: String => persist(payload)(handle)
      }

      def handle: Receive = {
        case payload: String =>
          sender ! payload
          sender ! lastSequenceNr
          sender ! recoveryRunning
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

     "write and replay 10K messages" in {
       val processor1 = system.actorOf(Props(classOf[ProcessorA], "p1"))
       1L to 10000L foreach { i =>
         processor1 ! s"a-$i"
         expectMsgAllOf(3.seconds, s"a-$i", i, false)
       }

       val processor2 = system.actorOf(Props(classOf[ProcessorA], "p1"))
       1L to 10000L foreach { i =>
         expectMsgAllOf(10.seconds, s"a-$i", i, true)
       }

       processor2 ! "b"
       expectMsgAllOf("b", 10001L, false)
     }
   }
}

