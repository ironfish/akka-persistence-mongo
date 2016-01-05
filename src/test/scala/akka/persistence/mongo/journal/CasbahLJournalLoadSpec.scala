package akka.persistence.mongo.journal

import akka.actor.{ActorRef, Props, ActorSystem, Actor}
import akka.persistence.PersistentActor
import akka.persistence.mongo.{EmbeddedMongoSupport, CasbahJournalCommon}
import akka.persistence.mongo.PortServer._
import akka.testkit.{ImplicitSender, TestKit}

import com.typesafe.config.ConfigFactory

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

object CasbahLJournalLoadSpec extends CasbahJournalCommon {
  override protected val rejectNonSerializableObjectsKey: String = s"$configRootKey.reject-non-serializable-objects"
  override protected val mongoUrlKey: String = s"$configRootKey.mongo-url"

  val config = ConfigFactory.parseString(
    s"""
       |akka.persistence.journal.plugin = "$configRootKey"
       |$mongoUrlKey = "mongodb://localhost:$freePort/store.messages"
       |$rejectNonSerializableObjectsKey = false
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

  class ProcessorA(val persistenceId: String) extends PersistentActor with Measure {
    def receiveRecover: Receive = handle

    def receiveCommand: Receive = {
      case c@"start" =>
        deferAsync(c) {
          _ => startMeasure()
            sender ! "started"
        }
      case c@"stop" =>
        deferAsync(c)(_ => stopMeasure())
      case payload: String =>
        persistAsync(payload)(handle)
    }

    def handle: Receive = {
      case payload: String =>
    }
  }

  class ProcessorB(val persistenceId: String, failAt: Option[Long], receiver: ActorRef) extends PersistentActor {
    def receiveRecover: Receive = handle

    def receiveCommand: Receive = {
      case payload: String => persistAsync(payload)(handle)
    }

    def handle: Receive = {
      case payload: String =>
        receiver ! s"$payload-$lastSequenceNr"
    }
  }
}

class CasbahLJournalLoadSpec extends TestKit(ActorSystem("test", CasbahLJournalLoadSpec.config))
  with EmbeddedMongoSupport
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll{

  import CasbahLJournalLoadSpec._

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

  "A Casbah journal" should {
    "have some reasonable write throughput" in {
      val warmCycles = 100L
      val loadCycles = 1000L

      val processor1 = system.actorOf(Props(classOf[ProcessorA], "p1a"))
      1L to warmCycles foreach { i => processor1 ! "a" }
      processor1 ! "start"
      expectMsg("started")
      1L to loadCycles foreach { i => processor1 ! "a" }
      processor1 ! "stop"
      expectMsgPF(100 seconds) {
        case throughput: Double ⇒ println(f"\nthroughput = $throughput%.2f persistent commands per second") }
    }
    "work properly under load" in {
      val cycles = 1000L

      val processor1 = system.actorOf(Props(classOf[ProcessorB], "p1b", None, self))
      1L to cycles foreach { i => processor1 ! "a" }
      1L to cycles foreach { i => expectMsg(s"a-$i") }

      val processor2 = system.actorOf(Props(classOf[ProcessorB], "p1b", None, self))
      1L to cycles foreach { i => expectMsg(s"a-$i") }

      processor2 ! "b"
      expectMsg(s"b-${cycles + 1L}")
    }
  }
}