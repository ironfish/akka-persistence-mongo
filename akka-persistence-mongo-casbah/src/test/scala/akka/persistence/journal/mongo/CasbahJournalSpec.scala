/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.journal.mongo

import akka.actor._
import akka.persistence._
import akka.testkit._

import com.typesafe.config.ConfigFactory

import org.scalatest._

import scala.concurrent.duration._

object CasbahJournalSpec {

  def config(port: Int) = ConfigFactory.parseString(
    s"""
      |akka.persistence.journal.plugin = "casbah-journal"
      |akka.persistence.snapshot-store.local.dir = "target/snapshots"
      |akka.persistence.publish-plugin-commands = on
      |akka.persistence.publish-confirmations = on
      |casbah-journal.mongo-url = "mongodb://localhost:$port/store.messages"
    """.stripMargin)

  case class Delete(snr: Long, permanent: Boolean)

  class ProcessorA(override val processorId: String) extends Processor {
    def receive = {
      case Persistent(payload, sequenceNr) =>
        sender ! payload
        sender ! sequenceNr
        sender ! recoveryRunning
      case Delete(sequenceNr, permanent) =>
        deleteMessage(sequenceNr, permanent)
    }
  }

  class ProcessorB(override val processorId: String) extends Processor {
    val destination = context.actorOf(Props[Destination])
    val channel = context.actorOf(Channel.props("channel"))

    def receive = {
      case p: Persistent =>
        channel forward Deliver(p, destination.path)
    }
  }

  class Destination extends Actor {
    def receive = {
      case cp @ ConfirmablePersistent(payload, sequenceNr, _) =>
        sender ! s"$payload-$sequenceNr"
        cp.confirm()
    }
  }

  class ProcessorC(override val processorId: String, probe: ActorRef) extends Processor {
    var last: String = _

    def receive = {
      case Persistent(payload: String, sequenceNr) =>
        last = s"$payload-$sequenceNr"
        probe ! s"updated-$last"
      case "snap" =>
        saveSnapshot(last)
      case SaveSnapshotSuccess(_) =>
        probe ! s"snapped-$last"
      case SnapshotOffer(_, snapshot: String) =>
        last = snapshot
        probe ! s"offered-$last"
      case Delete(sequenceNr, permanent) =>
        deleteMessage(sequenceNr, permanent)
    }
  }

  class ProcessorCNoRecover(override val processorId: String, probe: ActorRef) extends ProcessorC(processorId, probe) {
    override def preStart() = ()
  }
}

import CasbahJournalSpec._
import PortServer._

class CasbahJournalSpec extends TestKit(ActorSystem("test", config(freePort)))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with MongoCleanup {

  "A Casbah journal" should {

    "write and replay messages" in {
      val processor1 = system.actorOf(Props(classOf[ProcessorA], "p1"))
      1L to 16L foreach { i =>
        processor1 ! Persistent(s"a-$i")
        expectMsgAllOf(3.seconds, s"a-$i", i, false)
      }

      val processor2 = system.actorOf(Props(classOf[ProcessorA], "p1"))
      1L to 16L foreach { i =>
        expectMsgAllOf(s"a-$i", i, true)
      }

      processor2 ! Persistent("b")
      expectMsgAllOf("b", 17L, false)
    }

    "write delivery confirmations" in {
      val confirmProbe = TestProbe()
      subscribeToConfirmation(confirmProbe)

      val processor1 = system.actorOf(Props(classOf[ProcessorB], "p4"))
      1L to 16L foreach { i =>
        processor1 ! Persistent("a")
        awaitConfirmation(confirmProbe)
        expectMsg(s"a-$i")
      }

      val processor2 = system.actorOf(Props(classOf[ProcessorB], "p4"))
      processor2 ! Persistent("b")
      awaitConfirmation(confirmProbe)
      expectMsg("b-17")
    }

    "recover from a snapshot with follow-up messages" in {
      val processor1 = system.actorOf(Props(classOf[ProcessorC], "p5", testActor))
      processor1 ! Persistent("a")
      expectMsg("updated-a-1")
      processor1 ! "snap"
      expectMsg("snapped-a-1")
      processor1 ! Persistent("b")
      expectMsg("updated-b-2")

      system.actorOf(Props(classOf[ProcessorC], "p5", testActor))
      expectMsg("offered-a-1")
      expectMsg("updated-b-2")
    }

    "recover from a snapshot with follow-up messages and an upper bound" in {
      val processor1 = system.actorOf(Props(classOf[ProcessorCNoRecover], "p5a", testActor))
      processor1 ! Recover()
      processor1 ! Persistent("a")
      expectMsg("updated-a-1")
      processor1 ! "snap"
      expectMsg("snapped-a-1")
      2L to 7L foreach { i =>
        processor1 ! Persistent("a")
        expectMsg(s"updated-a-$i")
      }

      val processor2 = system.actorOf(Props(classOf[ProcessorCNoRecover], "p5a", testActor))
      processor2 ! Recover(toSequenceNr = 3L)
      expectMsg("offered-a-1")
      expectMsg("updated-a-2")
      expectMsg("updated-a-3")
      processor2 ! Persistent("d")
      expectMsg("updated-d-8")
    }

    "not replay messages marked as deleted" in {
      testDelete("p2", permanent = false)
    }

    "not replay permanently deleted messages" in {
      testDelete("p3", permanent = true)
    }

    def testDelete(processorId: String, permanent: Boolean) {
      val deleteProbe = TestProbe()
      subscribeToDeletion(deleteProbe)

      val processor1 = system.actorOf(Props(classOf[ProcessorA], processorId))
      1L to 16L foreach { i =>
        processor1 ! Persistent(s"a-$i")
        expectMsgAllOf(s"a-$i", i, false)
      }

      // delete single message
      processor1 ! Delete(12L, permanent)
      awaitDeletion(deleteProbe)

      system.actorOf(Props(classOf[ProcessorA], processorId))
      1L to 16L foreach { i =>
        if (i != 12L) expectMsgAllOf(s"a-$i", i, true)
      }

      // delete range
      6L to 10L foreach { i =>
        processor1 ! Delete(i, permanent)
        awaitDeletion(deleteProbe)
      }

      system.actorOf(Props(classOf[ProcessorA], processorId))
      1L to 5L foreach { i =>
        expectMsgAllOf(s"a-$i", i, true)
      }
      11L to 16L foreach { i =>
        if (i != 12L) expectMsgAllOf(s"a-$i", i, true)
      }
    }
  }

  def subscribeToConfirmation(probe: TestProbe): Unit =
    system.eventStream.subscribe(probe.ref, classOf[DeliveredByChannel])

  def awaitConfirmation(probe: TestProbe): Unit =
    probe.expectMsgType[DeliveredByChannel](max = 3.seconds)

  def subscribeToDeletion(probe: TestProbe): Unit =
    system.eventStream.subscribe(probe.ref, classOf[JournalProtocol.DeleteMessages])

  def awaitDeletion(probe: TestProbe): Unit =
    probe.expectMsgType[JournalProtocol.DeleteMessages](max = 3.seconds)
}
