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

case class Msg(deliveryId: Long, payload: Any)
case class Confirm(deliverId: Long)
case class MsgConfirmed(deliveryId: Long)

object CasbahIntegrationJournalSpec {

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

  case class DeleteTo(snr: Long, permanent: Boolean)

  class ProcessorA(val persistenceId: String) extends PersistentActor {
    def receiveRecover: Receive = handle

    def receiveCommand: Receive = {
      case DeleteTo(sequenceNr, permanent) =>
        deleteMessages(sequenceNr, permanent)
      case payload: String =>
        persist(payload)(handle)
    }

    def handle: Receive = {
      case payload: String =>
        sender ! payload
        sender ! lastSequenceNr
        sender ! recoveryRunning
    }
  }

  class ProcessorB(override val persistenceId: String) extends PersistentActor with AtLeastOnceDelivery {
    val destination = context.actorOf(Props[Destination])

    def receiveCommand: Receive = {
      case DeleteTo(sequenceNr, permanent) =>
        deleteMessages(sequenceNr, permanent)
      case Confirm(deliveryId) ⇒
          confirmDelivery(deliveryId)
          context.system.eventStream.publish(MsgConfirmed(deliveryId))
      case payload =>  persist(payload)(handle)
    }

    def handle: Receive = {
      case payload: String =>
        sender ! payload
        sender ! lastSequenceNr
        sender ! recoveryRunning

        deliver(destination.path, deliveryId ⇒ Msg(deliveryId, payload))
    }

    def receiveRecover: Receive = {
      case _ =>
    }

  }

  class Destination extends Actor {
    def receive = {
      case Msg(deliveryId, payload) => sender ! Confirm(deliveryId)
    }
  }

  class ProcessorC(val persistenceId: String, probe: ActorRef) extends PersistentActor {
    var last: String = _

    def receiveRecover: Receive = {
      case SnapshotOffer(_, snapshot: String) =>
        last = snapshot
        probe ! s"offered-$last"
      case payload: String =>
        handle(payload)
    }

    def receiveCommand: Receive = {
      case "snap" =>
        saveSnapshot(last)
      case SaveSnapshotSuccess(_) =>
        probe ! s"snapped-$last"
      case payload: String =>
        persist(payload)(handle)
    }

    def handle: Receive = {
      case payload: String =>
        last = s"$payload-$lastSequenceNr"
        probe ! s"updated-$last"
    }
  }

  class ProcessorCNoRecover(override val persistenceId: String, probe: ActorRef) extends ProcessorC(persistenceId, probe) {
    override def preStart() = ()
  }

  class ViewA(val viewId: String, val persistenceId: String, probe: ActorRef) extends PersistentView {
    def receive = {
      case payload =>
        probe ! payload
    }

    override def autoUpdate: Boolean = false
    override def autoUpdateReplayMax: Long = 0
  }
}

import CasbahIntegrationJournalSpec._
import akka.persistence.mongo.PortServer._

class CasbahIntegrationJournalSpec extends TestKit(ActorSystem("test", config(freePort)))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with MongoCleanup {

  override val actorSystem = system

  def subscribeToConfirmation(probe: TestProbe): Unit =
     system.eventStream.subscribe(probe.ref, classOf[MsgConfirmed])

   def subscribeToBatchDeletion(probe: TestProbe): Unit =
     system.eventStream.subscribe(probe.ref, classOf[JournalProtocol.DeleteMessages])

   def subscribeToRangeDeletion(probe: TestProbe): Unit =
     system.eventStream.subscribe(probe.ref, classOf[JournalProtocol.DeleteMessagesTo])

   def awaitBatchDeletion(probe: TestProbe): Unit =
     probe.expectMsgType[JournalProtocol.DeleteMessages]

   def awaitRangeDeletion(probe: TestProbe): Unit =
     probe.expectMsgType[JournalProtocol.DeleteMessagesTo]

  def awaitConfirmation(probe: TestProbe, confirm: MsgConfirmed): Unit =
    probe.expectMsg(3 seconds, confirm)

  def testRangeDelete(persistenceId: String, permanent: Boolean) {
    val deleteProbe = TestProbe()
    subscribeToRangeDeletion(deleteProbe)

    val processor1 = system.actorOf(Props(classOf[ProcessorA], persistenceId))
    1L to 16L foreach { i =>
      processor1 ! s"a-$i"
      expectMsgAllOf(s"a-$i", i, false)
    }

    processor1 ! DeleteTo(3L, permanent)
    awaitRangeDeletion(deleteProbe)

    system.actorOf(Props(classOf[ProcessorA], persistenceId))
    4L to 16L foreach { i =>
      expectMsgAllOf(s"a-$i", i, true)
    }

    processor1 ! DeleteTo(7L, permanent)
    awaitRangeDeletion(deleteProbe)

    system.actorOf(Props(classOf[ProcessorA], persistenceId))
    8L to 16L foreach { i =>
      expectMsgAllOf(s"a-$i", i, true)
    }
  }

  "A Casbah journal" should {

    "write and replay messages" in {
      val processor1 = system.actorOf(Props(classOf[ProcessorA], "p1"))
      1L to 16L foreach { i =>
        processor1 ! s"a-$i"
        expectMsgAllOf(3.seconds, s"a-$i", i, false)
      }

      val processor2 = system.actorOf(Props(classOf[ProcessorA], "p1"))
      1L to 16L foreach { i =>
        expectMsgAllOf(s"a-$i", i, true)
      }

      processor2 ! "b"
      expectMsgAllOf("b", 17L, false)
    }

    "write delivery confirmations" in {
      val confirmProbe = TestProbe()
      subscribeToConfirmation(confirmProbe)

      val processor1 = system.actorOf(Props(classOf[ProcessorB], "p2"))
      1L to 16L foreach { i =>
        processor1 ! s"a-$i"
        expectMsgAllOf(3.seconds, s"a-$i", i, false)
        awaitConfirmation(confirmProbe, MsgConfirmed(i))
      }

      val processor2 = system.actorOf(Props(classOf[ProcessorB], "p2"))
      processor2 ! "b"
      awaitConfirmation(confirmProbe, MsgConfirmed(1))
      expectMsgAllOf("b", 17L, false)
    }

    "not replay messages marked as range-deleted" in {
      testRangeDelete("p5", permanent = false)
    }

    "not replay permanently range-deleted messages" in {
      testRangeDelete("p6", permanent = true)
    }

    "replay messages incrementally" in {
       val probe = TestProbe()
       val processor1 = system.actorOf(Props(classOf[ProcessorA], "p7"))
       1L to 6L foreach { i =>
         processor1 ! s"a-$i"
         expectMsgAllOf(s"a-$i", i, false)
       }

       val view = system.actorOf(Props(classOf[ViewA], "p7-view", "p7", probe.ref))
       probe.expectNoMsg(200.millis)

       view ! Update(true, replayMax = 3L)
       probe.expectMsg(s"a-1")
       probe.expectMsg(s"a-2")
       probe.expectMsg(s"a-3")
       probe.expectNoMsg(200.millis)

       view ! Update(true, replayMax = 3L)
       probe.expectMsg(s"a-4")
       probe.expectMsg(s"a-5")
       probe.expectMsg(s"a-6")
       probe.expectNoMsg(200.millis)
     }

    "recover from a snapshot with follow-up messages" in {
      val processor1 = system.actorOf(Props(classOf[ProcessorC], "p8", testActor))
      processor1 ! "a"
      expectMsg("updated-a-1")
      processor1 ! "snap"
      expectMsg("snapped-a-1")
      processor1 ! "b"
      expectMsg("updated-b-2")

      system.actorOf(Props(classOf[ProcessorC], "p8", testActor))
      expectMsg("offered-a-1")
      expectMsg("updated-b-2")
    }

    "recover from a snapshot with follow-up messages and an upper bound" in {
      val processor1 = system.actorOf(Props(classOf[ProcessorCNoRecover], "p9", testActor))
      processor1 ! Recover()
      processor1 ! "a"
      expectMsg("updated-a-1")
      processor1 ! "snap"
      expectMsg("snapped-a-1")
      2L to 7L foreach { i =>
        processor1 ! "a"
        expectMsg(s"updated-a-$i")
      }

      val processor2 = system.actorOf(Props(classOf[ProcessorCNoRecover], "p9", testActor))
      processor2 ! Recover(toSequenceNr = 3L)
      expectMsg("offered-a-1")
      expectMsg("updated-a-2")
      expectMsg("updated-a-3")
      processor2 ! "d"
      expectMsg("updated-d-8")
    }

    "recover from a snapshot without follow-up messages inside a partition" in {
      val processor1 = system.actorOf(Props(classOf[ProcessorC], "p10", testActor))
      processor1 ! "a"
      expectMsg("updated-a-1")
      processor1 ! "snap"
      expectMsg("snapped-a-1")

      val processor2 = system.actorOf(Props(classOf[ProcessorC], "p10", testActor))
      expectMsg("offered-a-1")
      processor2 ! "b"
      expectMsg("updated-b-2")
    }

    "recover from a snapshot without follow-up messages at a partition boundary (where next partition is invalid)" in {
      val processor1 = system.actorOf(Props(classOf[ProcessorC], "p11", testActor))
      1L to 5L foreach { i =>
        processor1 ! "a"
        expectMsg(s"updated-a-$i")
      }
      processor1 ! "snap"
      expectMsg("snapped-a-5")

      val processor2 = system.actorOf(Props(classOf[ProcessorC], "p11", testActor))
      expectMsg("offered-a-5")
      processor2 ! "b"
      expectMsg("updated-b-6")
    }
  }
}
