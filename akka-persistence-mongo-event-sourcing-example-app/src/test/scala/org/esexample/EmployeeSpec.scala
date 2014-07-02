/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package org.esexample

import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import akka.actor.{ActorRef, Props, ActorSystem}

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

object EmployeeSpec {

  def config(port: Int) = ConfigFactory.parseString(
    s"""
      |akka.persistence.journal.plugin = "casbah-journal"
      |akka.persistence.snapshot-store.plugin = "casbah-snapshot-store"
      |akka.persistence.journal.max-deletion-batch-size = 3
      |akka.persistence.publish-plugin-commands = on
      |akka.persistence.publish-confirmations = on
      |akka.persistence.view.auto-update-interval = 1s
      |casbah-journal.mongo-journal-url = "mongodb://localhost:$port/store.messages"
      |casbah-journal.mongo-journal-write-concern = "acknowledged"
      |casbah-journal.mongo-journal-write-concern-timeout = 10000
      |casbah-snapshot-store.mongo-snapshot-url = "mongodb://localhost:$port/store.snapshots"
      |casbah-snapshot-store.mongo-snapshot-write-concern = "acknowledged"
      |casbah-snapshot-store.mongo-snapshot-write-concern-timeout = 10000
      |benefits-view.mongo-url = "mongodb://localhost:$port/hr.benefits"
      |benefits-view.channel = "benefits-channel"
      |benefits-view.destination = "benefits-destination"
    """.stripMargin)

  lazy val freePort = Network.getFreeServerPort
}

class EmployeeSpec extends TestKit(ActorSystem("test", EmployeeSpec.config(EmployeeSpec.freePort)))
    with ImplicitSender
    with WordSpecLike
    with MustMatchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  import EmployeeSpec._
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
    mongod.stop()
    mongodExe.stop()
  }

  val IdJonSmith = "121-33-4546"
  val StartDateMarch1st2014Midnight = 1393632000000L
  val DeactivateDateMay1st2014Midnight = 1398902400000L
  val ActivateDateMay2nd2014Midnight = 1398988800000L
  val TerminateDateMay3rd2014Midnight = 1399075200000L
  val RehireDateMay4th2014Midnight = 1399161600000L

  "The Application" must {
    "when issued a validated HireEmployee command, generate a persisted EmployeeHired event" in {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[EmployeeHired])
      val msg = HireEmployee(IdJonSmith, -1l, "smith", "jon", "123 Big Road", "Perkiomenville", "PA", "18074", "USA",
        StartDateMarch1st2014Midnight, "Technology", "The Total Package", BigDecimal(300000))
      employeeProcessor ! msg
      probe.expectMsgType[EmployeeHired]
    }
    "when issued a validated ChangeEmployeeLastName command, generate a persisted EmployeeLastNameChanged event" in {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[EmployeeLastNameChanged])
      val msg = ChangeEmployeeLastName(IdJonSmith, 0, "Smith")
      employeeProcessor ! msg
      probe.expectMsgType[EmployeeLastNameChanged]
    }
    "when issued a validated ChangeEmployeeFirstName command, generate a persisted EmployeeFirstNameChanged event" in {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[EmployeeFirstNameChanged])
      val msg = ChangeEmployeeFirstName(IdJonSmith, 1, "Jon")
      employeeProcessor ! msg
      probe.expectMsgType[EmployeeFirstNameChanged]
    }
    "when issued a validated ChangeEmployeeAddress command, generate a persisted EmployeeAddressChanged event" in {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[EmployeeAddressChanged])
      val msg = ChangeEmployeeAddress(IdJonSmith, 2, "4839 E. Trindle Road.", "Mechanicsburg", "PA", "USA", "17055")
      employeeProcessor ! msg
      probe.expectMsgType[EmployeeAddressChanged]
    }
    "when issued a validated ChangeEmployeeStartDate command, generate a persisted EmployeeStartDateChanged event" in {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[EmployeeStartDateChanged])
      val msg = ChangeEmployeeStartDate(IdJonSmith, 3, 1393977600000L)
      employeeProcessor ! msg
      probe.expectMsgType[EmployeeStartDateChanged]
    }
    "when issued a validated ChangeEmployeeDept command, generate a persisted EmployeeDeptChanged event" in {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[EmployeeDeptChanged])
      val msg = ChangeEmployeeDept(IdJonSmith, 4, "Computer Science")
      employeeProcessor ! msg
      probe.expectMsgType[EmployeeDeptChanged]
    }
    "when issued a validated ChangeEmployeeTitle command, generate a persisted EmployeeTitleChanged event" in {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[EmployeeTitleChanged])
      val msg = ChangeEmployeeTitle(IdJonSmith, 5, "World Traveller")
      employeeProcessor ! msg
      probe.expectMsgType[EmployeeTitleChanged]
    }
    "when issued a validated ChangeEmployeeSalary command, generate a persisted EmployeeSalaryChanged event" in {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[EmployeeSalaryChanged])
      val msg = ChangeEmployeeSalary(IdJonSmith, 6, BigDecimal(650000))
      employeeProcessor ! msg
      probe.expectMsgType[EmployeeSalaryChanged]
    }
    "when issued a validated DeactivateEmployee command, generate a persisted EmployeeDeactivated event" in {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[EmployeeDeactivated])
      val msg = DeactivateEmployee(IdJonSmith, 7, DeactivateDateMay1st2014Midnight)
      employeeProcessor ! msg
      probe.expectMsgType[EmployeeDeactivated]
    }
    "when issued a validated ActivateEmployee command, generate a persisted EmployeeActivated event" in {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[EmployeeActivated])
      val msg = ActivateEmployee(IdJonSmith, 8, ActivateDateMay2nd2014Midnight)
      employeeProcessor ! msg
      probe.expectMsgType[EmployeeActivated]
    }
    "when issued a validated TerminateEmployee command, generate a persisted EmployeeTerminated event" in {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[EmployeeTerminated])
      val msg = TerminateEmployee(IdJonSmith, 9, TerminateDateMay3rd2014Midnight, "Tired")
      employeeProcessor ! msg
      probe.expectMsgType[EmployeeTerminated]
    }
    "when issued a validated RehireEmployee command, generate a persisted EmployeeRehired event" in {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[EmployeeRehired])
      val msg = RehireEmployee(IdJonSmith, 10, RehireDateMay4th2014Midnight)
      employeeProcessor ! msg
      probe.expectMsgType[EmployeeRehired]
    }
    "when issued a RunPayroll request for two employees, two EmployeePaid events are persisted" in {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[EmployeePaid])
      val msg = HireEmployee("2", -1l, "Sean", "Walsh", "321 Large Ave.", "Rumson", "NJ", "07760", "USA", 1393632000000L,
        "Technology", "The Brain", BigDecimal(300000))
      employeeProcessor ! msg
      employeeProcessor ! RunPayroll
      probe.expectMsg(EmployeePaid(IdJonSmith, 12, BigDecimal(5000)))
      probe.expectMsg(EmployeePaid("2", 1, BigDecimal(5000)))
    }
    "when issued a invalid HireEmployee command, an ErrorMessage is returned" in {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[EmployeeHired])
      val msg = HireEmployee("3", -1l, "David", "Johnson", "888 Small Street", "Rumson", "NJ", "07760", "USA", 1399150441L,
        "IT", "Intern", BigDecimal(30000))
      employeeProcessor ! msg
      expectMsgType[ErrorMessage]
      probe.expectNoMsg()
    }
    "when issued a ChangeEmployeeLastName for a non-existent employee, an ErrorMessage is returned" in {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ChangeEmployeeLastName])
      val msg = ChangeEmployeeLastName("3", 0, "Johnson")
      employeeProcessor ! msg
      expectMsgType[ErrorMessage]
      probe.expectNoMsg()
    }
    "when issued a ChangeEmployeeLastName with the incorrect version, an ErrorMessage is returned" in {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ChangeEmployeeLastName])
      val msg = ChangeEmployeeLastName("2", 0, "Walshy")
      employeeProcessor ! msg
      expectMsgType[ErrorMessage]
      probe.expectNoMsg()
    }
    "when issued a GetEmployee command respond with the appropriate employee" in {
      employeeProcessor ! GetEmployee("2")
      expectMsg(Some(ActiveEmployee("2",1,"Sean","Walsh",Address("321 Large Ave.","Rumson","NJ","USA","07760"),1393632000000L,
        "Technology","The Brain",300000,295000)))
    }
  }
}
