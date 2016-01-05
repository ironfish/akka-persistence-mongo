package akka.persistence.mongo.journal

import akka.actor._
import akka.persistence.PersistentRepr
import akka.persistence.mongo.CasbahCommon
import akka.persistence.mongo.EmbeddedMongoSupport
import akka.persistence.mongo.PortServer._
import com.mongodb.casbah.Imports._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object CasbahJournalRootSpec extends CasbahCommon {
  override protected val configRootKey: String = "casbah-journal"
  override protected val mongoUrlKey: String = s"$configRootKey.mongo-url"

  val pidOne: String = "p-1"
  val pidTwo: String = "p-2"
  val minSnr: Long = 1L
  val maxSnr: Long = 10L

  val config = ConfigFactory.parseString(
    s"""
       |akka.persistence.snapshot-store.plugin = "$configRootKey"
       |$mongoUrlKey = "mongodb://localhost:$freePort/store.snapshots"
    """.stripMargin)
}

class CasbahJournalRootSpec extends EmbeddedMongoSupport
  with CasbahJournalRoot
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll { mixin : ActorLogging =>

  override val actorSystem: ActorSystem = ActorSystem("test", CasbahJournalRootSpec.config)
  override protected val config: Config = actorSystem.settings.config.getConfig(configRootKey)

  implicit val concern: WriteConcern = writeConcern

  import CasbahJournalRoot._
  import CasbahJournalRootSpec._

  override def beforeAll(): Unit = {
    embeddedMongoStartup()
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    mongoDB.dropDatabase()
    client.close()
    actorSystem.terminate()
    Await.result(actorSystem.whenTerminated, Duration.Inf)
    embeddedMongoShutdown()
  }

  def persistentRepr(persistenceId: String, sequenceNr: Long) =
    PersistentRepr(payload = s"a-$sequenceNr", sequenceNr = sequenceNr, persistenceId = persistenceId)

  def messages(persistenceId: String): immutable.Seq[PersistentRepr] = {
    var msgs: immutable.Seq[PersistentRepr] = List.empty[PersistentRepr]
    minSnr to maxSnr foreach(snr => msgs = msgs :+ persistentRepr(persistenceId, snr))
    msgs
  }

  "A CasbahJournalRoot" should {
    "return 0L for highest sequenceNr if none exist" in {
      val pid: String = "none"
      highestSequenceNrExecute(mongoCollection, pid) shouldBe 0L
    }
    "persist valid messages" in {
      persistExecute(mongoCollection, messages(pidOne).flatMap {
        message => persistentReprToDBObjectExecute(message, toBytes)(rejectNonSerializableObjects = false).toOption
      })
    }
    "return the highest sequenceNr for a given persistenceId" in {
      highestSequenceNrExecute(mongoCollection, pidOne) shouldBe maxSnr
    }
    "return all persisted messages for a given pid on replay" in {
      val iter: Iterator[PersistentRepr] =
        replayCursorExecute(mongoCollection, pidOne, minSnr, maxSnr, maxSnr.toInt, fromBytes[PersistentRepr])
      iter.size shouldBe 10
      var snrCntr: Long = 1
      iter.foreach { pr =>
        pr.persistenceId shouldBe pidOne
        pr.sequenceNr shouldBe snrCntr
        snrCntr += 1
      }
    }
    "deleteTo the provided sequenceNr" in {
      var msgs: immutable.Seq[PersistentRepr] = List.empty[PersistentRepr]
      minSnr to maxSnr foreach(snr => msgs = msgs :+ persistentRepr(pidTwo, snr))
      persistExecute(mongoCollection, messages(pidTwo).flatMap {
        message => persistentReprToDBObjectExecute(message, toBytes)(rejectNonSerializableObjects = false).toOption
      })
      deleteToExecute(mongoCollection, concern, pidTwo, 5L, toBytes)
      val iter: Iterator[PersistentRepr] =
        replayCursorExecute(mongoCollection, pidTwo, minSnr, maxSnr, maxSnr.toInt, fromBytes[PersistentRepr])
      iter.size shouldBe 5
      var snrCntr: Long = 6
      iter.foreach { pr =>
        pr.persistenceId shouldBe pidTwo
        pr.sequenceNr shouldBe snrCntr
        snrCntr += 1
      }
    }
    "not replay when all entries for a given pid have been deleted" in {
      deleteToExecute(mongoCollection, concern, pidTwo, maxSnr, toBytes)
      val iter: Iterator[PersistentRepr] =
        replayCursorExecute(mongoCollection, pidTwo, minSnr, maxSnr, maxSnr.toInt, fromBytes[PersistentRepr])
      iter.size shouldBe 0
    }
    "should retain highest sequence number when all entries for a given pid have been deleted" in {
      highestSequenceNrExecute(mongoCollection, pidTwo) shouldBe maxSnr
    }
  }
}
