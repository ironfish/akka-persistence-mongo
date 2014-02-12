/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.journal.mongo

import java.util.concurrent.atomic.AtomicInteger

import akka.actor._
import akka.persistence._
import akka.persistence.JournalProtocol._
import akka.testkit._

import com.typesafe.config.ConfigFactory

import org.scalatest.{Matchers, BeforeAndAfterEach, WordSpecLike}

import scala.collection.immutable.Seq
import scala.reflect.ClassTag

object CasbahJournalSpec {
  def config(port: Int) = ConfigFactory.parseString(
    s"""
      |akka.persistence.journal.plugin = "casbah-journal"
      |akka.persistence.journal.max-deletion-batch-size = 3
      |akka.persistence.snapshot-store.local.dir = "target/snapshots"
      |akka.persistence.publish-plugin-commands = on
      |akka.persistence.publish-confirmations = on
      |casbah-journal.mongo-url = "mongodb://localhost:$port/store.messages"
    """.stripMargin)
}

import CasbahJournalSpec._
import PortServer._

class CasbahJournalSpec extends TestKit(ActorSystem("test2", config(freePort)))
    with WordSpecLike
    with Matchers
    with BeforeAndAfterEach
    with MongoCleanup { this: TestKit =>

  val counter = new AtomicInteger(0)
  var extension: Persistence = _
  var journal: ActorRef = _

  var pid: String = _

  var senderProbe: TestProbe = _
  var receiverProbe: TestProbe = _

  // Due to a problem w/ embedded mongo starting up, the extension and journal require initialization in beforeAll
  override def beforeAll() {
    super.beforeAll()
    extension = Persistence(system)
    journal = extension.journalFor(null)
  }

  override def beforeEach(): Unit = {
    senderProbe = TestProbe()
    receiverProbe = TestProbe()

    pid = s"p-${counter.incrementAndGet()}"
    writeMessages(1, 5, pid, senderProbe.ref)
  }

  def writeMessages(from: Int, to: Int, pid: String, sender: ActorRef): Unit = {
    val msgs = from to to map { i => PersistentRepr(payload = s"a-$i", sequenceNr = i, processorId = pid, sender = sender) }
    val probe = TestProbe()

    journal ! WriteMessages(msgs, probe.ref)

    probe.expectMsg(WriteMessagesSuccess)
    from to to foreach { i =>
      probe.expectMsgPF() { case WriteMessageSuccess(PersistentImpl(payload, `i`, `pid`, _, _, `sender`)) => payload should be (s"a-$i") }
    }
  }

  def replayedMessage(snr: Long, deleted: Boolean = false, confirms: Seq[String] = Nil): ReplayedMessage =
    ReplayedMessage(PersistentImpl(s"a-$snr", snr, pid, deleted, confirms, senderProbe.ref))

  def subscribe[T : ClassTag](subscriber: ActorRef) =
    system.eventStream.subscribe(subscriber, implicitly[ClassTag[T]].runtimeClass)

  "A journal" must {
    "replay all messages" in {
      journal ! ReplayMessages(1, Long.MaxValue, Long.MaxValue, pid, receiverProbe.ref)
      1 to 5 foreach { i => receiverProbe.expectMsg(replayedMessage(i)) }
      receiverProbe.expectMsg(ReplayMessagesSuccess)
    }
    "replay messages using a lower sequence number bound" in {
      journal ! ReplayMessages(3, Long.MaxValue, Long.MaxValue, pid, receiverProbe.ref)
      3 to 5 foreach { i => receiverProbe.expectMsg(replayedMessage(i)) }
      receiverProbe.expectMsg(ReplayMessagesSuccess)
    }
    "replay messages using an upper sequence number bound" in {
      journal ! ReplayMessages(1, 3, Long.MaxValue, pid, receiverProbe.ref)
      1 to 3 foreach { i => receiverProbe.expectMsg(replayedMessage(i)) }
      receiverProbe.expectMsg(ReplayMessagesSuccess)
    }
    "replay messages using a count limit" in {
      journal ! ReplayMessages(1, Long.MaxValue, 3, pid, receiverProbe.ref)
      1 to 3 foreach { i => receiverProbe.expectMsg(replayedMessage(i)) }
      receiverProbe.expectMsg(ReplayMessagesSuccess)
    }
    "replay messages using a lower and upper sequence number bound" in {
      journal ! ReplayMessages(2, 4, Long.MaxValue, pid, receiverProbe.ref)
      2 to 4 foreach { i => receiverProbe.expectMsg(replayedMessage(i)) }
      receiverProbe.expectMsg(ReplayMessagesSuccess)
    }
    "replay messages using a lower and upper sequence number bound and a count limit" in {
      journal ! ReplayMessages(2, 4, 2, pid, receiverProbe.ref)
      2 to 3 foreach { i => receiverProbe.expectMsg(replayedMessage(i)) }
      receiverProbe.expectMsg(ReplayMessagesSuccess)
    }
    "replay a single if lower equals upper sequence number bound" in {
      journal ! ReplayMessages(2, 2, Long.MaxValue, pid, receiverProbe.ref)
      2 to 2 foreach { i => receiverProbe.expectMsg(replayedMessage(i)) }
      receiverProbe.expectMsg(ReplayMessagesSuccess)
    }
    "replay a single message if count limit equals 1" in {
      journal ! ReplayMessages(2, 4, 1, pid, receiverProbe.ref)
      2 to 2 foreach { i => receiverProbe.expectMsg(replayedMessage(i)) }
      receiverProbe.expectMsg(ReplayMessagesSuccess)
    }
    "not replay messages if count limit equals 0" in {
      journal ! ReplayMessages(2, 4, 0, pid, receiverProbe.ref)
      receiverProbe.expectMsg(ReplayMessagesSuccess)
    }
    "not replay messages if lower is greater than upper sequence number bound" in {
      journal ! ReplayMessages(3, 2, Long.MaxValue, pid, receiverProbe.ref)
      receiverProbe.expectMsg(ReplayMessagesSuccess)
    }
    "not replay permanently deleted messages (individual deletion)" in {
      val msgIds = List(PersistentIdImpl(pid, 3), PersistentIdImpl(pid, 4))
      journal ! DeleteMessages(msgIds, permanent = true, Some(receiverProbe.ref))
      receiverProbe.expectMsg(DeleteMessagesSuccess(msgIds))

      journal ! ReplayMessages(1, Long.MaxValue, Long.MaxValue, pid, receiverProbe.ref)
      List(1, 2, 5) foreach { i => receiverProbe.expectMsg(replayedMessage(i)) }
      receiverProbe.expectMsg(ReplayMessagesSuccess)
    }
    "not replay permanently deleted messages (range deletion)" in {
      val cmd = DeleteMessagesTo(pid, 3, permanent = true)
      val sub = TestProbe()

      journal ! cmd
      subscribe[DeleteMessagesTo](sub.ref)
      sub.expectMsg(cmd)

      journal ! ReplayMessages(1, Long.MaxValue, Long.MaxValue, pid, receiverProbe.ref)
      List(4, 5) foreach { i => receiverProbe.expectMsg(replayedMessage(i)) }
      receiverProbe.expectMsg(ReplayMessagesSuccess)
    }
    "replay logically deleted messages with deleted=true (individual deletion)" in {
      val msgIds = List(PersistentIdImpl(pid, 3), PersistentIdImpl(pid, 4))
      journal ! DeleteMessages(msgIds, permanent = false, Some(receiverProbe.ref))
      receiverProbe.expectMsg(DeleteMessagesSuccess(msgIds))

      journal ! ReplayMessages(1, Long.MaxValue, Long.MaxValue, pid, receiverProbe.ref, replayDeleted = true)
      1 to 5 foreach {
        case i@(1 | 2 | 5) => receiverProbe.expectMsg(replayedMessage(i))
        case i@(3 | 4) => receiverProbe.expectMsg(replayedMessage(i, deleted = true))
      }
      receiverProbe.expectMsg(ReplayMessagesSuccess)
    }
    "replay logically deleted messages with deleted=true (range deletion)" in {
      val cmd = DeleteMessagesTo(pid, 3, permanent = false)
      val sub = TestProbe()

      journal ! cmd
      subscribe[DeleteMessagesTo](sub.ref)
      sub.expectMsg(cmd)

      journal ! ReplayMessages(1, Long.MaxValue, Long.MaxValue, pid, receiverProbe.ref, replayDeleted = true)
      1 to 5 foreach {
        case i@(1 | 2 | 3) => receiverProbe.expectMsg(replayedMessage(i, deleted = true))
        case i@(4 | 5) => receiverProbe.expectMsg(replayedMessage(i))
      }
      receiverProbe.expectMsg(ReplayMessagesSuccess)
    }
    "tolerate orphan deletion markers" in {
      val msgIds = List(PersistentIdImpl(pid, 3), PersistentIdImpl(pid, 4))
      journal ! DeleteMessages(msgIds, permanent = true, Some(receiverProbe.ref)) // delete message
      receiverProbe.expectMsg(DeleteMessagesSuccess(msgIds))

      journal ! DeleteMessages(msgIds, permanent = false, Some(receiverProbe.ref)) // write orphan marker
      receiverProbe.expectMsg(DeleteMessagesSuccess(msgIds))

      journal ! ReplayMessages(1, Long.MaxValue, Long.MaxValue, pid, receiverProbe.ref)
      List(1, 2, 5) foreach { i => receiverProbe.expectMsg(replayedMessage(i)) }
      receiverProbe.expectMsg(ReplayMessagesSuccess)
    }
  }
}
