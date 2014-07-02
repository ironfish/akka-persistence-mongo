/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package org.esexample

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{ActorRef, Props, ActorSystem}

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import com.typesafe.config.ConfigFactory

import concurrent.duration._

import de.flapdoodle.embed.process.runtime.Network
import de.flapdoodle.embed.process.io.directories.PlatformTempDir
import de.flapdoodle.embed.process.extract.UUIDTempNaming
import de.flapdoodle.embed.mongo.{MongodStarter, Command}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.io.{NullProcessor, Processors}
import de.flapdoodle.embed.process.config.IRuntimeConfig
import de.flapdoodle.embed.mongo.config._

import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, MustMatchers, WordSpecLike}

object BenefitsSpec {

  def config(port: Int) = ConfigFactory.parseString(
    s"""
      |akka.persistence.journal.plugin = "casbah-journal"
      |akka.persistence.snapshot-store.plugin = "casbah-snapshot-store"
      |akka.persistence.journal.max-deletion-batch-size = 3
      |akka.persistence.publish-plugin-commands = on
      |akka.persistence.publish-confirmations = on
      |akka.persistence.view.auto-update-interval = 1s
      |casbah-journal.mongo-journal-url = "mongodb://localhost:$port/store2.messages"
      |casbah-journal.mongo-journal-write-concern = "acknowledged"
      |casbah-journal.mongo-journal-write-concern-timeout = 10000
      |casbah-snapshot-store.mongo-snapshot-url = "mongodb://localhost:$port/store2.snapshots"
      |casbah-snapshot-store.mongo-snapshot-write-concern = "acknowledged"
      |casbah-snapshot-store.mongo-snapshot-write-concern-timeout = 10000
      |benefits-view.mongo-url = "mongodb://localhost:$port/hr2.benefits"
      |benefits-view.channel = "benefits-channel"
      |benefits-view.destination = "benefits-destination"
    """.stripMargin)

  lazy val freePort = Network.getFreeServerPort
}

class BenefitsSpec extends TestKit(ActorSystem("test-benefits", BenefitsSpec.config(BenefitsSpec.freePort)))
    with ImplicitSender
    with WordSpecLike
    with MustMatchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  import BenefitsSpec._
  import EmployeeProtocol._

  lazy val host = "localhost"
  lazy val port = freePort
  lazy val localHostIPV6 = Network.localhostIsIPv6()

  val artifactStorePath = new PlatformTempDir()
  val executableNaming = new UUIDTempNaming()
  val command = Command.MongoD
  val version = Version.Main.PRODUCTION

  // Used to filter out console output messages.
  val processOutput = new ProcessOutput(
    Processors.named("[mongod>]", new NullProcessor),
    Processors.named("[MONGOD>]", new NullProcessor),
    Processors.named("[console>]", new NullProcessor))

  val runtimeConfig: IRuntimeConfig =
    new RuntimeConfigBuilder()
      .defaults(command)
      .processOutput(processOutput)
      .artifactStore(new ArtifactStoreBuilder()
      .defaults(command)
      .download(new DownloadConfigBuilder()
      .defaultsForCommand(command)
      .artifactStorePath(artifactStorePath))
      .executableNaming(executableNaming))
      .build()

  val mongodConfig =
    new MongodConfigBuilder()
      .version(version)
      .net(new Net(port, localHostIPV6))
      .build()

  lazy val mongodStarter = MongodStarter.getInstance(runtimeConfig)
  lazy val mongod = mongodStarter.prepare(mongodConfig)
  lazy val mongodExe = mongod.start()

  val duration = 10.seconds
  var employeeProcessor: ActorRef = _
  var benefitsView: ActorRef = _

  override def beforeAll() = {
    mongodExe
    employeeProcessor = system.actorOf(Props[EmployeeProcessor])
    benefitsView = system.actorOf(Props[BenefitsView])
  }

  override def afterAll() = {
    system.shutdown()
    system.awaitTermination(duration)
    client.close()
    mongod.stop()
    mongodExe.stop()
  }

  val IdJonSmith = "121-33-4546"
  val StartDateMarch1st2014Midnight = 1393632000000L
  val DeactivateDateMay1st2014Midnight = 1398902400000L
  val ActivateDateMay2nd2014Midnight = 1398988800000L
  val TerminateDateMay3rd2014Midnight = 1399075200000L
  val RehireDateMay4th2014Midnight = 1399161600000L

  lazy val uri = MongoClientURI(BenefitsSpec.config(freePort).getString("benefits-view.mongo-url"))
  lazy val client =  MongoClient(uri)
  lazy val db = client(uri.database.get)
  lazy val coll = db(uri.collection.get)

  "The Application" must {
    "when persisted EmployeeHired view persists associated BenefitDates" in {
      employeeProcessor ! HireEmployee(IdJonSmith, -1l, "smith", "jon", "123 Big Road", "Perkiomenville", "PA", "18074", "USA",
        StartDateMarch1st2014Midnight, "Technology", "The Total Package", BigDecimal(300000))
      val Expected = Some(BenefitDates(IdJonSmith, StartDateMarch1st2014Midnight, Nil, Nil, Nil))
      awaitCond({
        val dbo = coll.findOne(MongoDBObject("employeeId" -> IdJonSmith))
        if (!dbo.isDefined) false
        else if (Some(grater[BenefitDates].asObject(dbo.get)) == Expected) true
        else false
      }, duration, 500 milliseconds, "BenefitDates read side failure.")
    }
    "when persisted EmployeeDeactivated view persists associated BenefitDates" in {
      employeeProcessor ! DeactivateEmployee(IdJonSmith, 0L, DeactivateDateMay1st2014Midnight)
      val Expected = Some(BenefitDates(IdJonSmith, StartDateMarch1st2014Midnight, List(DeactivateDateMay1st2014Midnight), Nil, Nil))
      awaitCond({
        val dbo = coll.findOne(MongoDBObject("employeeId" -> IdJonSmith))
        if (!dbo.isDefined) false
        else if (Some(grater[BenefitDates].asObject(dbo.get)) == Expected) true
        else false
      }, duration, 500 milliseconds, "BenefitDates read side failure.")
    }
    "when persisted EmployeeActivated view persists associated BenefitDates" in {
      employeeProcessor ! ActivateEmployee(IdJonSmith, 1L, ActivateDateMay2nd2014Midnight)
      val Expected = Some(BenefitDates(IdJonSmith, ActivateDateMay2nd2014Midnight, List(DeactivateDateMay1st2014Midnight), Nil, Nil))
      awaitCond({
        val dbo = coll.findOne(MongoDBObject("employeeId" -> IdJonSmith))
        if (!dbo.isDefined) false
        else if (Some(grater[BenefitDates].asObject(dbo.get)) == Expected) true
        else false
      }, duration, 500 milliseconds, "BenefitDates read side failure.")
    }
    "when persisted EmployeeTerminated view persists associated BenefitDates" in {
      employeeProcessor ! TerminateEmployee(IdJonSmith, 2L, TerminateDateMay3rd2014Midnight, "Tired")
      val Expected = Some(BenefitDates(IdJonSmith, ActivateDateMay2nd2014Midnight, List(DeactivateDateMay1st2014Midnight),
        List(TerminateDateMay3rd2014Midnight), Nil))
      awaitCond({
        val dbo = coll.findOne(MongoDBObject("employeeId" -> IdJonSmith))
        if (!dbo.isDefined) false
        else if (Some(grater[BenefitDates].asObject(dbo.get)) == Expected) true
        else false
      }, duration, 500 milliseconds, "BenefitDates read side failure.")
    }
    "when persisted EmployeeRehired view persists associated BenefitDates" in {
      employeeProcessor ! RehireEmployee(IdJonSmith, 3L, RehireDateMay4th2014Midnight)
      val Expected = Some(BenefitDates(IdJonSmith, ActivateDateMay2nd2014Midnight, List(DeactivateDateMay1st2014Midnight),
        List(TerminateDateMay3rd2014Midnight), List(RehireDateMay4th2014Midnight)))
      awaitCond({
      val dbo = coll.findOne(MongoDBObject("employeeId" -> IdJonSmith))
        if (!dbo.isDefined) false
        else if (Some(grater[BenefitDates].asObject(dbo.get)) == Expected) true
        else false
      }, duration, 500 milliseconds, "BenefitDates read side failure.")
    }
  }
}
