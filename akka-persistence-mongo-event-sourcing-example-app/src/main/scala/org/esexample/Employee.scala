package org.esexample

import akka.actor.{Props, ActorSystem}
import akka.persistence.{SnapshotOffer, EventsourcedProcessor}

import org.joda.time.{DateTimeZone, DateTime}

import scalaz._
import Scalaz._

/**
* These are the sealed commands of every action that may be performed by an employee. Commands can be rejected.
*/
sealed trait EmployeeCommand {
  def id: String
  def expectedVersion: Long
}
final case class HireEmployee(id: String, expectedVersion: Long = -1L, lastName: String, firstName: String, street: String,
  city: String, stateOrProvince: String, postalCode: String, country: String, startDate: Long, dept: String, title: String,
  salary: BigDecimal) extends EmployeeCommand
final case class ChangeEmployeeLastName(id: String, expectedVersion: Long, lastName: String) extends EmployeeCommand
final case class ChangeEmployeeFirstName(id: String, expectedVersion: Long, firstName: String) extends EmployeeCommand
final case class ChangeEmployeeAddress(id: String, expectedVersion: Long, street: String, city: String, stateOrProvince: String,
  country: String, postalCode: String) extends EmployeeCommand
final case class ChangeEmployeeStartDate(id: String, expectedVersion: Long, startDate: Long) extends EmployeeCommand
final case class ChangeEmployeeDept(id: String, expectedVersion: Long, dept: String) extends EmployeeCommand
final case class ChangeEmployeeTitle(id: String, expectedVersion: Long, title: String) extends EmployeeCommand
final case class ChangeEmployeeSalary(id: String, expectedVersion: Long, salary: BigDecimal) extends EmployeeCommand

/**
 * These are the resulting events from commands performed by an employee if the commands were not rejected. They will be journaled
 * in the event store and cannot be rejected.
*/
sealed trait EmployeeEvent {
  def id: String
  def version: Long
}
final case class EmployeeHired(id: String, version: Long, lastName: String, firstName: String, street: String, city: String,
  stateOrProvince: String, postalCode: String, country: String, startDate: Long, dept: String, title: String,
  salary: BigDecimal) extends EmployeeEvent
final case class EmployeeLastNameChanged(id: String, version: Long, lastName: String) extends EmployeeEvent
final case class EmployeeFirstNameChanged(id: String, version: Long, firstName: String) extends EmployeeEvent
final case class EmployeeAddressChanged(id: String, version: Long, street: String, city: String, stateOrProvince: String,
  country: String, postalCode: String) extends EmployeeEvent
final case class EmployeeStartDateChanged(id: String, version: Long, startDate: Long) extends EmployeeEvent
final case class EmployeeDeptChanged(id: String, version: Long, dept: String) extends EmployeeEvent
final case class EmployeeTitleChanged(id: String, version: Long, title: String) extends EmployeeEvent
final case class EmployeeSalaryChanged(id: String, version: Long, salary: BigDecimal) extends EmployeeEvent

case object SnapshotEmployees

case object EmployeeExists extends ValidationKey
case object IdRequired extends ValidationKey
case object ExpectedVersionMismatch extends ValidationKey
case object VersionInvalid extends ValidationKey
case object LastNameRequired extends ValidationKey
case object FirstNameRequired extends ValidationKey
case object StartDateNotOnDayBoundary extends ValidationKey
case object DepartmentRequired extends ValidationKey
case object TitleRequired extends ValidationKey
case object InvalidSalary extends ValidationKey

/**
* An employee domain aggregate to be extended by NewHire, Termination, etc.
*/
sealed trait Employee {
  def id: String
  def version: Long
  def lastName: String
  def firstName: String
  def address: Address
  def startDate: Long
  def dept: String
  def title: String
  def salary: BigDecimal
}

/**
* Companion object for alert abstraction that handles version validation.
*/
object Employee {
  def requireVersion[A <: Employee](e: A, cmd: EmployeeCommand): DomainValidation[A] = {
    if (cmd.expectedVersion == e.version) e.successNel
    else s"$e version does not match $cmd version".failNel //ExpectedVersionMismatch.failNel
  }
}

trait EmployeeValidations {
  def checkAndIncrementVersion(l: Long): Validation[String, Long] =
    if (l < -1) VersionInvalid.failure else (l + 1).success

  def checkStartDate(d: Long): Validation[String, Long] = {
    val dt = new DateTime(d, DateTimeZone.UTC)
    if (dt.getMillis != dt.withTimeAtStartOfDay.getMillis) StartDateNotOnDayBoundary.failure else d.success
  }

  def checkSalary(s: BigDecimal): Validation[String, BigDecimal] = {
    val OneMillionDollars = BigDecimal(1000000)
    if (s < 0 || s > OneMillionDollars) InvalidSalary.failure else s.success
  }
}

/**
 * Active employee state. This case class is private an must be created the ActiveEmployee companion object.
 */
case class ActiveEmployee (
  id: String,
  version: Long,
  lastName: String,
  firstName: String,
  address: Address,
  startDate: Long,
  dept: String,
  title: String,
  salary: BigDecimal) extends Employee with EmployeeValidations {
  import CommonValidations._

  def withLastName(lastName: String): DomainValidation[ActiveEmployee] =
    checkString(lastName, LastNameRequired) fold (f => f.failNel, s => copy(version = version + 1, lastName = s).success)

  def withFirstName(firstName: String): DomainValidation[ActiveEmployee] =
    checkString(firstName, FirstNameRequired) fold (f => f.failNel, s => copy(version = version + 1, firstName = s).success)

  def withAddress(street: String, city: String, stateOrProvince: String, country: String,
      postalCode: String): DomainValidation[ActiveEmployee] =
    Address.validate(street, city, stateOrProvince, country, postalCode) fold (f => f.fail, s => copy(version = version + 1,
      address = s).success)

  def withStartDate(startDate: Long): DomainValidation[ActiveEmployee] =
    checkStartDate(startDate) fold (f => f.failNel, s => copy(version = version + 1, startDate = s).success)

  def withDept(dept: String): DomainValidation[ActiveEmployee] =
    checkString(dept, DepartmentRequired) fold (f => f.failNel, s => copy(version = version + 1, dept = s).success)

  def withTitle(title: String): DomainValidation[ActiveEmployee] =
    checkString(dept, TitleRequired) fold (f => f.failNel, s => copy(version = version + 1, title = s).success)

  def withSalary(salary: BigDecimal): DomainValidation[ActiveEmployee] =
    checkSalary(salary) fold (f => f.failNel, s => copy(version = version + 1, salary = s).success)
}

object ActiveEmployee extends EmployeeValidations {
  import CommonValidations._

    def validate(cmd: HireEmployee): DomainValidation[ActiveEmployee] =
    (checkString(cmd.id, IdRequired).toValidationNel |@|
      checkAndIncrementVersion(cmd.expectedVersion).toValidationNel |@|
      checkString(cmd.lastName, LastNameRequired).toValidationNel |@|
      checkString(cmd.firstName, FirstNameRequired).toValidationNel |@|
      Address.validate(cmd.street, cmd.city, cmd.stateOrProvince, cmd.country, cmd.postalCode) |@|
      checkStartDate(cmd.startDate).toValidationNel |@|
      checkString(cmd.dept, DepartmentRequired).toValidationNel |@|
      checkString(cmd.title, TitleRequired).toValidationNel |@|
      checkSalary(cmd.salary).toValidationNel) {
    ActiveEmployee(_, _, _, _, _, _, _, _, _)
  }
}

final case class EmployeeState(employees: Map[String, Employee] = Map.empty) {
  def update(e: Employee) = copy(employees = employees + (e.id -> e))
  def get(id: String) = employees.get(id)
  def getActive(id: String) = get(id).map(_.asInstanceOf[ActiveEmployee])
//  def getActive(id: String) = employees.get(id).get.asInstanceOf[ActiveEmployee]
}

class EmployeeProcessor extends EventsourcedProcessor {

  var state = EmployeeState()

  def updateState(emp: Employee): Unit =
    state = state.update(emp)

  /**
   * These are the events that are recovered during journal recovery. They cannot fail and must be processed to recreate the current
   * state of the aggregate.
   */
  val receiveRecover: Receive = {
    case evt: EmployeeHired =>
      updateState(ActiveEmployee(evt.id, evt.version, evt.lastName, evt.firstName, Address(evt.street,
        evt.city, evt.stateOrProvince, evt.country, evt.postalCode), evt.startDate, evt.dept, evt.title, evt.salary))
    case evt: EmployeeLastNameChanged =>
      updateState(state.getActive(evt.id).get.copy(version = evt.version, lastName = evt.lastName))
    case evt: EmployeeFirstNameChanged =>
      updateState(state.getActive(evt.id).get.copy(version = evt.version, firstName = evt.firstName))
    case evt: EmployeeAddressChanged =>
      updateState(state.getActive(evt.id).get.copy(version = evt.version, address = Address(evt.street, evt.city,
      evt.stateOrProvince, evt.country, evt.postalCode)))
    case evt: EmployeeStartDateChanged =>
      updateState(state.getActive(evt.id).get.copy(version = evt.version, startDate = evt.startDate))
    case evt: EmployeeDeptChanged =>
      updateState(state.getActive(evt.id).get.copy(version = evt.version, dept = evt.dept))
    case evt: EmployeeTitleChanged =>
      updateState(state.getActive(evt.id).get.copy(version = evt.version, title = evt.title))
    case evt: EmployeeSalaryChanged =>
      updateState(state.getActive(evt.id).get.copy(version = evt.version, salary = evt.salary))
    case SnapshotOffer(_, snapshot: EmployeeState) => state = snapshot
  }

  /**
   * These are the commands that are requested. As command they can fail sending response back to the user. Each command will
   * generate one or more events to be journaled.
   */
  val receiveCommand: Receive = {
    case cmd: HireEmployee => hire(cmd) fold (
      f => println(s"error $f occurred on $cmd"), // todo send back to sender
      s => persist(EmployeeHired(s.id, s.version, s.lastName, s.firstName, s.address.street, s.address.city,
        s.address.stateOrProvince, s.address.postalCode, s.address.country, s.startDate, s.dept, s.title, s.salary)) { event =>
          updateState(s)
          context.system.eventStream.publish(event)
        })
    case cmd: ChangeEmployeeLastName => changeLastName(cmd) fold (
      f => println(s"error $f occurred on $cmd"), // todo send back to sender
      s => persist(EmployeeLastNameChanged(s.id, s.version, s.lastName)) { event =>
        updateState(s)
        context.system.eventStream.publish(event)
      })
    case cmd: ChangeEmployeeFirstName => changeFirstName(cmd) fold (
      f => println(s"error $f occurred on $cmd"), // todo send back to sender
      s => persist(EmployeeFirstNameChanged(s.id, s.version, s.firstName)) { event =>
        updateState(s)
        context.system.eventStream.publish(event)
      })
    case cmd: ChangeEmployeeAddress => changeAddress(cmd) fold (
      f => println(s"error $f occurred on $cmd"), // todo send back to sender
      s => persist(EmployeeAddressChanged(s.id, s.version, s.address.street, s.address.city, s.address.stateOrProvince,
        s.address.country, s.address.postalCode)) { event =>
          updateState(s)
          context.system.eventStream.publish(event)
      })
    case cmd: ChangeEmployeeStartDate => changeStartDate(cmd) fold (
      f => println(s"error $f occurred on $cmd"), // todo send back to sender
      s => persist(EmployeeStartDateChanged(s.id, s.version, s.startDate)) { event =>
        updateState(s)
        context.system.eventStream.publish(event)
      })
    case cmd: ChangeEmployeeDept => changeDept(cmd) fold (
      f => println(s"error $f occurred on $cmd"), // todo send back to sender
      s => persist(EmployeeDeptChanged(s.id, s.version, s.dept)) { event =>
        updateState(s)
        context.system.eventStream.publish(event)
      })
    case cmd: ChangeEmployeeTitle => changeTitle(cmd) fold (
      f => println(s"error $f occurred on $cmd"), // todo send back to sender
      s => persist(EmployeeTitleChanged(s.id, s.version, s.title)) { event =>
        updateState(s)
        context.system.eventStream.publish(event)
      })
    case cmd: ChangeEmployeeSalary => changeSalary(cmd) fold (
      f => println(s"error $f occurred on $cmd"), // todo send back to sender
      s => persist(EmployeeSalaryChanged(s.id, s.version, s.salary)) { event =>
        updateState(s)
        context.system.eventStream.publish(event)
      })
    case SnapshotEmployees  => saveSnapshot(state)
    case "print"            => println("STATE: " + state)
  }

  def hire(cmd: HireEmployee): DomainValidation[ActiveEmployee] =
    state.get(cmd.id) match {
      case Some(emp) => EmployeeExists.failNel
      case None      => ActiveEmployee.validate(cmd)
    }

  def changeLastName(cmd: ChangeEmployeeLastName): DomainValidation[ActiveEmployee] =
    updateActive(cmd) { ae => ae.withLastName(cmd.lastName) }

  def changeFirstName(cmd: ChangeEmployeeFirstName): DomainValidation[ActiveEmployee] =
    updateActive(cmd) { ae => ae.withFirstName(cmd.firstName) }

  def changeAddress(cmd: ChangeEmployeeAddress): DomainValidation[ActiveEmployee] =
    updateActive(cmd) { ae => ae.withAddress(cmd.street, cmd.city, cmd.stateOrProvince, cmd.country, cmd.postalCode) }

  def changeStartDate(cmd: ChangeEmployeeStartDate): DomainValidation[ActiveEmployee] =
    updateActive(cmd) { ae => ae.withStartDate(cmd.startDate) }

  def changeDept(cmd: ChangeEmployeeDept): DomainValidation[ActiveEmployee] =
    updateActive(cmd) { ae => ae.withDept(cmd.dept) }

  def changeTitle(cmd: ChangeEmployeeTitle): DomainValidation[ActiveEmployee] =
    updateActive(cmd) { ae => ae.withTitle(cmd.title) }

  def changeSalary(cmd: ChangeEmployeeSalary): DomainValidation[ActiveEmployee] =
    updateActive(cmd) { ae => ae.withSalary(cmd.salary) }

  def updateEmployee[A <: Employee](cmd: EmployeeCommand)(fn: Employee => DomainValidation[A]): DomainValidation[A] =
    state.get(cmd.id) match {
      case Some(emp) => Employee.requireVersion(emp, cmd) fold (f => f.fail, s => fn(s))
      case None      => s"employee for $cmd does not exist".failNel
    }

  def updateActive[A <: Employee](cmd: EmployeeCommand)(fn: ActiveEmployee => DomainValidation[A]): DomainValidation[A] =
    updateEmployee(cmd) {
      case emp: ActiveEmployee => fn(emp)
      case emp                 => s"$emp for $cmd is not active".failNel
    }
}

object EmployeeProcessorExample extends App {

  val system = ActorSystem("employee-example")
  val processor = system.actorOf(Props[EmployeeProcessor], "employee-processor")

  processor ! "print"

  processor ! HireEmployee("1", -1l, "Devore", "duncan", "946 Henning Road", "Perkiomenville", "PA", "18074", "USA", 1393632000000L,
    "Technology", "The Total Package", BigDecimal(300000))

  processor ! HireEmployee("2", -1l, "Sean", "Walsh", "14 Rosalie Ave.", "Rumson", "NJ", "07760", "USA", 1399150441L,
    "Technology", "The Brain", BigDecimal(300000))

  processor ! "print"

  processor ! ChangeEmployeeLastName("1", 0, "DeVore")

  processor ! SnapshotEmployees

  processor ! ChangeEmployeeLastName("3", 0, "Smith")

  processor ! ChangeEmployeeFirstName("1", 1, "Duncan")

  processor ! HireEmployee("2", -1l, "Sean", "Walsh", "14 Rosalie Ave.", "Rumson", "NJ", "07760", "USA", 1393632000000L,
    "Technology", "The Brain", BigDecimal(300000))

  processor ! ChangeEmployeeFirstName("3", 0, "Smith")

  processor ! "print"

  Thread.sleep(1000)
  system.shutdown()
}