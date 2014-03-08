package akka.persistence.mongo.journal

import akka.actor.{ Props, ActorSystem, Actor }
import akka.persistence.{ Persistent, Processor }
import akka.persistence.mongo.MongoCleanup
import akka.testkit.{ ImplicitSender, TestKit }

import com.typesafe.config.ConfigFactory

import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.control.NoStackTrace

object CasbahLoadSpec {

  def config(port: Int) = ConfigFactory.parseString(
    s"""
      |akka.persistence.journal.plugin = "casbah-journal"
      |akka.persistence.snapshot-store.plugin = "casbah-snapshot-store"
      |akka.persistence.publish-plugin-commands = on
      |akka.persistence.publish-confirmations = on
      |casbah-journal.mongo-journal-url = "mongodb://localhost:$port/store.messages"
      |casbah-journal.mongo-journal-write-concern = "acknowledged"
      |casbah-journal.mongo-journal-write-concern-timeout = 10000
      |casbah-snapshot-store.mongo-snapshot-url = "mongodb://localhost:$port/store.snapshots"
      |casbah-snapshot-store.mongo-snapshot-write-concern = "acknowledged"
      |casbah-snapshot-store.mongo-snapshot-write-concern-timeout = 10000
    """.stripMargin)

  trait Measure extends { this: Actor ⇒
    val NanoToSecond = 1000.0 * 1000 * 1000

    var startTime: Long = 0L
    var stopTime: Long = 0L

    var startSequenceNr = 0L
    var stopSequenceNr = 0L

    def startMeasure(): Unit = {
      startSequenceNr = lastSequenceNr
      startTime = System.nanoTime
    }

    def stopMeasure(): Unit = {
      stopSequenceNr = lastSequenceNr
      stopTime = System.nanoTime
      sender ! (NanoToSecond * (stopSequenceNr - startSequenceNr) / (stopTime - startTime))
    }

    def lastSequenceNr: Long
  }

  class ProcessorA(override val processorId: String) extends Processor with Measure {
    def receive = {
      case "start" => startMeasure()
      case "stop" => stopMeasure()
      case Persistent(payload: String, sequenceNr) =>
    }
  }

  class ProcessorB(override val processorId: String, failAt: Option[Long]) extends Processor {
    def receive = {
      case Persistent(payload: String, sequenceNr) =>
        failAt.foreach(snr => if (snr == sequenceNr) throw new Exception("boom") with NoStackTrace)
        sender ! s"$payload-$sequenceNr"
    }

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      message match {
        case Some(p: Persistent) => deleteMessage(p.sequenceNr)
        case _ =>
      }
      super.preRestart(reason, message)
    }
  }
}

import CasbahLoadSpec._
import akka.persistence.mongo.PortServer._

class CasbahLoadSpec extends TestKit(ActorSystem("test", config(freePort)))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with MongoCleanup {

  override val actorSystem = system

  "A Casbah journal" should {
    "have some reasonable write throughput" in {
      val warmCycles = 100L
      val loadCycles = 1000L

      val processor1 = system.actorOf(Props(classOf[ProcessorA], "p1a"))
      1L to warmCycles foreach { i => processor1 ! Persistent("a") }
      processor1 ! "start"
      1L to loadCycles foreach { i => processor1 ! Persistent("a") }
      processor1 ! "stop"
      expectMsgPF(100 seconds) { case throughput: Double ⇒ println(f"\nthroughput = $throughput%.2f persistent commands per second") }
    }
    "work properly under load" in {
      val cycles = 1000L

      val processor1 = system.actorOf(Props(classOf[ProcessorB], "p1b", None))
      1L to cycles foreach { i => processor1 ! Persistent("a") }
      1L to cycles foreach { i => expectMsg(s"a-$i") }

      val processor2 = system.actorOf(Props(classOf[ProcessorB], "p1b", None))
      1L to cycles foreach { i => expectMsg(s"a-$i") }

      processor2 ! Persistent("b")
      expectMsg(s"b-${cycles + 1L}")
    }
    "work properly under load and failure conditions" in {
      val cycles = 1000L
      val failAt = 217L

      val processor1 = system.actorOf(Props(classOf[ProcessorB], "p1c", Some(failAt)))
      1L to cycles foreach { i => processor1 ! Persistent("a") }
      1L until failAt foreach { i => expectMsg(s"a-$i") }
      1L to cycles foreach { i => if (i != failAt) expectMsg(s"a-$i") }

      val processor2 = system.actorOf(Props(classOf[ProcessorB], "p1c", None))
      1L to cycles foreach { i => if (i != failAt) expectMsg(s"a-$i") }

      processor2 ! Persistent("b")
      expectMsg(s"b-${cycles + 1L}")
    }
  }
}
