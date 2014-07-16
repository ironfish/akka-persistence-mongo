package org.example

import concurrent.duration._
import org.scalatest._
import akka.actor.{PoisonPill, ActorRef, Props, ActorSystem}
import scala.concurrent.stm.Ref
import scala.concurrent.Await
import akka.testkit.{TestProbe, TestKit}
import de.flapdoodle.embed.process.runtime.Network
import de.flapdoodle.embed.process.io.directories.PlatformTempDir
import de.flapdoodle.embed.process.extract.UUIDTempNaming
import de.flapdoodle.embed.mongo.{MongodStarter, Command}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.io.{NullProcessor, Processors}
import de.flapdoodle.embed.process.config.IRuntimeConfig
import de.flapdoodle.embed.mongo.config._
import com.typesafe.config.ConfigFactory
import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._

/**
 * This spec uses embedded mongo on a random open port to simulate a new hire, termination, and rehire of
 * an employee.  Recover is also demonstrated.
 */
class EmployeeSpec extends TestKit(ActorSystem("test", EmployeeSpec.config(EmployeeSpec.freePort)))
  with WordSpecLike
  with MustMatchers
  with BeforeAndAfterAll
  with BeforeAndAfterEach {

  import EmployeeSpec._

  // Begin embedded mongo setup.
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
  // End embedded mongo.

  val duration = 10.seconds

  var ref: Ref[Map[String, Employee]] = _
  var employeeProcessor, benefits: ActorRef = _
  var employeeService: EmployeeService = _

  val Id = "111-222-3333"
  val LastName = "Walsh"
  val LastNameChanged = "Doback"
  val FirstName = "Sean"
  val Line1 = "200 Park Ave"
  val Line2 = "Apt 3C"
  val City = "New York"
  val State = "NY"
  val Country = "USA"
  val Postal = "10030"
  val StartDateMarch1_2014 = 1393632000000L
  val Dept = "Technology"
  val Title = "King"
  val TermDateApril1_2014 = 1396396800000L
  val RehireDateMay1_2014 =  1398902400000L

  override def beforeAll(): Unit = {
    // Here we essentially bootstrap our test application.
    mongodExe
    ref = Ref(Map.empty[String, Employee])
    benefits = system.actorOf(Props(new Benefits("localhost", freePort)), name = "benefits")

    employeeProcessor = system.actorOf(Props(new EmployeeProcessor(ref, benefits.path)), name = "employee-processor")
    employeeService = new EmployeeService(ref, employeeProcessor)
  }

  override def afterAll(): Unit = {
    // Cleanup
    system.shutdown()
    system.awaitTermination(10.seconds)
    mongod.stop()
    mongodExe.stop()
  }

  def subscribeToEventStream(probe: TestProbe, clazz: Class[_]) = system.eventStream.subscribe(probe.ref, clazz)

  "The Application" must {
    "hire a new employee and update CQRS read side benefits persistence" in {
      val confirmProbe = TestProbe()
      subscribeToEventStream(confirmProbe, classOf[MsgConfirmed])

      Await.result(employeeService.sendCommand(HireEmployee(Id, LastName, FirstName, Line1, Line2, City, State, Country,
        Postal, StartDateMarch1_2014, Dept, Title, BigDecimal(50000))), duration).isSuccess must be(right = true)
      employeeService.getAll.size must be(1)
      employeeService.getAllActive.size must be(1)

      val con = MongoClient("localhost", freePort)

      val col = con("hr")("benefits")
      val Expected = Some(EmployeeBenefits(Id, StartDateMarch1_2014, Nil, Nil))

      awaitCond({
        val dbo = col.findOne(MongoDBObject("employeeId" -> Id))
        if (!dbo.isDefined) false
        else if (Some(grater[EmployeeBenefits].asObject(dbo.get)) == Expected) true
        else false
      }, duration, 100 milliseconds, "Benefits read side failure.")

      confirmProbe.expectMsgClass(3 seconds, classOf[MsgConfirmed])
    }

    "fail change the employee last name with null and receive validation" in {
      Await.result(employeeService.sendCommand(ChangeEmployeeLastName(Id, null, 0L)), duration).isFailure must be(true)
    }

    "succeed in changing the employee last name" in {
      Await.result(employeeService.sendCommand(ChangeEmployeeLastName(Id, LastNameChanged, 0L)), duration).isSuccess must be(right = true)
      val emp = employeeService.get(Id)
      emp.isDefined must be(true)
      emp.get.lastName must be(LastNameChanged)
      emp.get.version must be(1L)
    }

    "terminate the employee and update CQRS read side benefits persistence" in {
      val confirmProbe = TestProbe()
      subscribeToEventStream(confirmProbe, classOf[MsgConfirmed])

      Await.result(employeeService.sendCommand(TerminateEmployee(Id, TermDateApril1_2014, "too studly", 1L)), duration).isSuccess must be(right = true)
      employeeService.getAll.size must be(1)
      employeeService.getAllActive.size must be(0)
      employeeService.getAllTerminated.size must be(1)

      val con = MongoClient("localhost", freePort)
      val col = con("hr")("benefits")
      val Expected = Some(EmployeeBenefits(Id, StartDateMarch1_2014, List(TermDateApril1_2014), Nil))

      awaitCond({
        val dbo = col.findOne(MongoDBObject("employeeId" -> Id))
        if (!dbo.isDefined) false
        else if (Some(grater[EmployeeBenefits].asObject(dbo.get)) == Expected) true
        else false
      }, duration, 100 milliseconds, "Benefits read side failure.")

      confirmProbe.expectMsgClass(3 seconds, classOf[MsgConfirmed])
    }

    "rehire the employee and update CQRS read side benefits persistence" in {
      val confirmProbe = TestProbe()
      subscribeToEventStream(confirmProbe, classOf[MsgConfirmed])

      val res = Await.result(employeeService.sendCommand(RehireEmployee(Id, RehireDateMay1_2014, 2L)), duration)
      res.isSuccess must be(right = true)
      employeeService.getAll.size must be(1)
      employeeService.getAllActive.size must be(1)
      employeeService.getAllTerminated.size must be(0)

      val con = MongoClient("localhost", freePort)
      val col = con("hr")("benefits")
      val Expected = Some(EmployeeBenefits(Id, StartDateMarch1_2014, List(TermDateApril1_2014), List(RehireDateMay1_2014)))

      awaitCond({
        val dbo = col.findOne(MongoDBObject("employeeId" -> Id))
        if (!dbo.isDefined) false
        else if (Some(grater[EmployeeBenefits].asObject(dbo.get)) == Expected) true
        else false
      }, duration, 100 milliseconds, "Benefits read side failure.")

      confirmProbe.expectMsgClass(3 seconds, classOf[MsgConfirmed])
    }

    "snapshot the employee" in {
      val confirmProbe = TestProbe()
      subscribeToEventStream(confirmProbe, classOf[SnapshotConfirmed])

      employeeProcessor ! SnapshotEmployee(Id)
      confirmProbe.expectMsgAllClassOf(3 seconds, classOf[SnapshotConfirmed])
    }

    "recover with no updates to CQRS read side benefits persistence" in {
      // Kill off old persistence infrastructure.
      val probe1, probe2 = TestProbe()
      probe1 watch benefits
      probe2 watch employeeProcessor
      benefits ! PoisonPill
      employeeProcessor ! PoisonPill
      probe1.expectTerminated(benefits)
      probe2.expectTerminated(employeeProcessor)

      // Reinitialize persistence infrastructure and ensure recovery.
      ref = Ref(Map.empty[String, Employee])
      val probe4 = TestProbe()
      benefits = system.actorOf(Props(new Benefits("localhost", freePort)), name = "benefits")
      probe4 watch benefits
      employeeProcessor = system.actorOf(Props(new EmployeeProcessor(ref, benefits.path)), name = "employee-processor")
      employeeService = new EmployeeService(ref, employeeProcessor)
      awaitCond(ref.single.get.values.size == 1, duration, 100 milliseconds, "Employees did not recover.")
      probe4.expectNoMsg(500 milliseconds)
    }
  }
}

object EmployeeSpec {

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

  lazy val freePort = Network.getFreeServerPort
}
