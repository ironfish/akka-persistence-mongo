package org.example

import scala.concurrent.duration._
import scala.concurrent.stm.Ref
import scala.concurrent.Future
import akka.actor.{ActorPath, ActorRef, ActorSystem}
import akka.pattern.ask
import akka.persistence._
import akka.util.Timeout
import scalaz._
import Scalaz._
import org.joda.time.{DateTimeZone, DateTime}

/**
 * An employee domain aggregate to be extended by NewHire, Termination, etc.
 */
sealed trait Employee {
  def id: String
  def lastName: String
  def firstName: String
  def address: Address
  def startDate: Long
  def dept: String
  def title: String
  def salary: BigDecimal
  def version: Long
}

/**
 * A companion object shared among Employee implementations for version management.
 */
object Employee {
  def requireVersion[T <: Employee](emp: T, expectedVersion: Long): DomainValidation[T] = {
    if (expectedVersion == emp.version) emp.successNel
    else "ExpectedVersionMismatch".failNel
  }
}

/**
 * A trait for validating salary.
 */
trait EmployeeValidations {

  def checkSalary(s: BigDecimal): Validation[String, BigDecimal] = {
    val OneMillionDollars = BigDecimal(1000000)
    if (s < OneMillionDollars && s > 0) s.success else "InvalidSalary".failure
  }

  def checkStartDate(d: Long): Validation[String, Long] = {
    val dt = new DateTime(d, DateTimeZone.UTC)
    if (dt.getMillis == dt.withTimeAtStartOfDay.getMillis) d.success else "StartDateNotOnDayBoundry".failure
  }

  def checkAndIncrementVersion(l: Long): Validation[String, Long] =
    if (1 > -1) (l + 1).success else "InvalidLongValue".failure
}

/**
 * An active employee.
 */
case class ActiveEmployee private (
  id: String,
  lastName: String,
  firstName: String,
  address: Address,
  startDate: Long,
  dept: String,
  title: String,
  salary: BigDecimal,
  version: Long
) extends Employee with EmployeeValidations {

  import CommonValidations._

  def withLastName(lastName: String): DomainValidation[ActiveEmployee] =
    checkString(lastName).fold(e => e.failNel, l => copy(version = version + 1, lastName = l).success)

  def withFirstName(firstName: String): DomainValidation[ActiveEmployee] =
    checkString(lastName).fold(e => e.failNel, f => copy(version = version + 1, firstName = f).success)

  def withAddress(line1: String, line2: String, city: String, stateOrProvince: String, country: String, postalCode: String): DomainValidation[ActiveEmployee] =
    Address.create(line1, line2, city, stateOrProvince, country, postalCode).fold(e => e.fail, a => copy(version = version + 1, address = a).success)

  def withStartDate(startDate: Long): DomainValidation[ActiveEmployee] =
    checkStartDate(startDate).fold(e => e.failNel, s => copy(version = version + 1, startDate = s).success)

  def withDept(dept: String): DomainValidation[ActiveEmployee] =
    checkString(dept).fold(e => e.failNel, d => copy(version = version + 1, dept = d).success)

  def withTitle(title: String): DomainValidation[ActiveEmployee] =
    checkString(dept).fold(e => e.failNel, t => copy(version = version + 1, title = t).success)

  def withSalary(salary: BigDecimal): DomainValidation[ActiveEmployee] =
    checkSalary(salary).fold(e => e.failNel, s => copy(version = version + 1, salary = s).success)

  def leaveOfAbsence: DomainValidation[InactiveEmployee] =
    InactiveEmployee.create(this)

  def terminate(terminationDate: Long, terminationReason: String): DomainValidation[Termination] =
    Termination.create(this, terminationDate, terminationReason)
}

/**
 * Companion object for NewHire used for creation.
 */
object ActiveEmployee extends EmployeeValidations {
  import CommonValidations._

  /**
   * Creates a new hire using non breaking validations.
   */
  def create(id: String, lastName: String, firstName: String, addressLine1: String, addressLine2: String, city: String, stateOrProvince: String,
             country: String, postalCode: String, startDate: Long, dept: String, title: String, salary: BigDecimal, version: Long): DomainValidation[ActiveEmployee] =
    (checkString(id).toValidationNel |@|
      checkString(lastName).toValidationNel |@|
      checkString(firstName).toValidationNel |@|
      Address.create(addressLine1, addressLine2, city, stateOrProvince, country, postalCode) |@|
      checkStartDate(startDate).toValidationNel |@|
      checkString(dept).toValidationNel |@|
      checkString(title).toValidationNel |@|
      checkSalary(salary).toValidationNel |@|
      checkAndIncrementVersion(version).toValidationNel) {
    ActiveEmployee(_, _, _, _, _, _, _, _, _)
  }
}

/**
 * This represents an inactive employee that does not collect paychecks but is accumulating benefits.
 */
case class InactiveEmployee private (
  id: String,
  lastName: String,
  firstName: String,
  address: Address,
  startDate: Long,
  dept: String,
  title: String,
  salary: BigDecimal,
  version: Long
  ) extends Employee {

  def activate: DomainValidation[ActiveEmployee] =
    ActiveEmployee.create(id, lastName, firstName, address.line1, address.line2, address.city, address.stateOrProvince, address.country, address.postalCode,
      startDate, dept, title, salary, version)
}

/**
 * Companion object for InactiveEmployee used for creation.
 */
object InactiveEmployee extends EmployeeValidations {
  import CommonValidations._

  /**
   * Creates a inactive employee using non breaking validations.
   */
  def create(ae: ActiveEmployee): DomainValidation[InactiveEmployee] =
    (checkString(ae.id).toValidationNel |@|
      checkString(ae.lastName).toValidationNel |@|
      checkString(ae.firstName).toValidationNel |@|
      Address.create(ae.address.line1, ae.address.line2, ae.address.city, ae.address.stateOrProvince, ae.address.country, ae.address.postalCode) |@|
      checkStartDate(ae.startDate).toValidationNel |@|
      checkString(ae.dept).toValidationNel |@|
      checkString(ae.title).toValidationNel |@|
      checkSalary(ae.salary).toValidationNel |@|
      checkAndIncrementVersion(ae.version).toValidationNel) {
      InactiveEmployee(_, _, _, _, _, _, _, _, _)
    }
}

/**
 * This represents a terminated employee.
 */
case class Termination private (
  id: String,
  lastName: String,
  firstName: String,
  address: Address,
  startDate: Long,
  dept: String,
  title: String,
  salary: BigDecimal,
  termDate: Long,
  termReason: String,
  version: Long
) extends Employee {

  def rehire(rehireDate: Long): DomainValidation[ActiveEmployee] =
    ActiveEmployee.create(id, lastName, firstName, address.line1, address.line2, address.city, address.stateOrProvince, address.country, address.postalCode,
      rehireDate, dept, title, salary, version)
}

/**
 * Companion object for Termination used for creation.
 */
object Termination extends EmployeeValidations {
  import CommonValidations._

  /**
   * Creates a termination using non breaking validations.
   */
  def create(ae: ActiveEmployee, termDate: Long, termReason: String): DomainValidation[Termination] =
    (checkString(ae.id).toValidationNel |@|
      checkString(ae.lastName).toValidationNel |@|
      checkString(ae.firstName).toValidationNel |@|
      Address.create(ae.address.line1, ae.address.line2, ae.address.city, ae.address.stateOrProvince, ae.address.country, ae.address.postalCode) |@|
      checkDate(ae.startDate).toValidationNel |@|
      checkString(ae.dept).toValidationNel |@|
      checkString(ae.title).toValidationNel |@|
      checkSalary(ae.salary).toValidationNel |@|
      checkStartDate(termDate).toValidationNel |@|
      checkString(termReason).toValidationNel |@|
      checkAndIncrementVersion(ae.version).toValidationNel) {
        Termination(_, _, _, _, _, _, _, _, _, _, _)
    }
}

/**
* These are the sealed commands of every action that may be performed by an employee.
*/
sealed trait EmployeeCommand
case class HireEmployee(id: String, lastName: String, firstName: String, addressLine1: String, addressLine2: String, city: String, stateOrProvince: String,
                        postalCode: String, country: String, startDate: Long, dept: String, title: String, salary: BigDecimal) extends EmployeeCommand
case class ChangeEmployeeLastName(id: String, lastName: String, expectedVersion: Long) extends EmployeeCommand
case class ChangeEmployeeFirstName(id: String, firstName: String, expectedVersion: Long) extends EmployeeCommand
case class ChangeEmployeeAddress(id: String, line1: String, line2: String, city: String, stateOrProvince: String, country: String, postalCode: String, expectedVersion: Long) extends EmployeeCommand
case class ChangeEmployeeStartDate(id: String, startDate: Long, expectedVersion: Long) extends EmployeeCommand
case class ChangeEmployeeDept(id: String, dept: String, expectedVersion: Long) extends EmployeeCommand
case class ChangeEmployeeTitle(id: String, title: String, expectedVersion: Long) extends EmployeeCommand
case class ChangeEmployeeSalary(id: String, salary: BigDecimal, expectedVersion: Long) extends EmployeeCommand
case class DeactivateEmployee(id: String, expectedVersion: Long) extends EmployeeCommand
case class ActivateEmployee(id: String, expectedVersion: Long) extends EmployeeCommand
case class TerminateEmployee(id: String, termDate: Long, termReason: String, expectedVersion: Long) extends EmployeeCommand
case class RehireEmployee(id: String, rehireDate: Long, expectedVersion: Long) extends EmployeeCommand
case class RunPayroll() extends EmployeeCommand

/**
 * These are the resulting events from commands performed by an employee.  Since this application is command sourcing
 * these events are informational only upon successful validations on the commands.
 */
sealed trait EmployeeEvent { def version: Long }
case class EmployeeHired(id: String, lastName: String, firstName: String, addressLine1: String, addressLine2: String, city: String,
                           stateOrProvince: String, postalCode: String, country: String, startDate: Long, dept: String, title: String, salary: BigDecimal, version: Long) extends EmployeeEvent
case class EmployeeLastNameChanged(id: String, lastName: String, version: Long) extends EmployeeEvent
case class EmployeeFirstNameChanged(id: String, firstName: String, version: Long) extends EmployeeEvent
case class EmployeeAddressChanged(id: String, line1: String, line2: String, city: String, stateOrProvince: String, country: String, postalCode: String, version: Long) extends EmployeeEvent
case class EmployeeStartDateChanged(id: String, startDate: Long, version: Long) extends EmployeeEvent
case class EmployeeDeptChanged(id: String, dept: String, version: Long) extends EmployeeEvent
case class EmployeeTitleChanged(id: String, title: String, version: Long) extends EmployeeEvent
case class EmployeeSalaryChanged(id: String, salary: BigDecimal, version: Long) extends EmployeeEvent
case class EmployeeDeactivated(id: String, version: Long) extends EmployeeEvent
case class EmployeeActivated(id: String, version: Long) extends EmployeeEvent
case class EmployeeTerminated(id: String, termDate: Long, termReason: String, version: Long) extends EmployeeEvent
case class EmployeeRehired(id: String, rehireDate: Long, version: Long) extends EmployeeEvent
case class EmployeePaid(id: String, salary: BigDecimal, version: Long) extends EmployeeEvent

sealed trait SnapshotEvent
case class SnapshotEmployee(id: String) extends SnapshotEvent

/**
 * This is the command sourcing processor for employees.
 * @param ref Ref[Map[String, Employee]] the in memory current state of all employees.
 * @param eventDestination ActorPath the listener of the events.
 */
class EmployeeProcessor(ref: Ref[Map[String, Employee]], eventDestination: ActorPath)
  extends PersistentActor
  with AtLeastOnceDelivery {

  override def persistenceId = "employee-persistence"

  val receiveCommand: Receive = {
    case cmd =>
      cmd match {
        case cmd: HireEmployee => process(hire(cmd)) { emp ⇒
          persist(EmployeeHired(emp.id, emp.lastName, emp.firstName, emp.address.line1, emp.address.line2, emp.address.city,emp.address.stateOrProvince, emp.address.country, emp.address.postalCode, emp.startDate, emp.dept, emp.title, emp.salary, emp.version))(updateState)
        }
        case cmd: ChangeEmployeeLastName => process(changeLastName(cmd)) { emp ⇒
          persist(EmployeeLastNameChanged(emp.id, emp.lastName, emp.version))(updateState)
        }
        case cmd: ChangeEmployeeFirstName => process(changeFirstName(cmd)) { emp ⇒
          persist(EmployeeFirstNameChanged(emp.id, emp.firstName, emp.version))(updateState)
        }
        case cmd: ChangeEmployeeStartDate => process(changeStartDate(cmd)) { emp ⇒
          persist(EmployeeStartDateChanged(emp.id, emp.startDate, emp.version))(updateState)
        }
        case cmd: ChangeEmployeeDept => process(changeDept(cmd)) { emp ⇒
          persist(EmployeeDeptChanged(emp.id, emp.dept, emp.version))(updateState)
        }
        case cmd: ChangeEmployeeTitle => process(changeTitle(cmd)) { emp ⇒
          persist(EmployeeTitleChanged(emp.id, emp.title, emp.version))(updateState)
        }
        case cmd: ChangeEmployeeSalary => process(changeSalary(cmd)) { emp ⇒
          persist(EmployeeSalaryChanged(emp.id, emp.salary, emp.version))(updateState)
        }
        case cmd: ChangeEmployeeAddress => process(changeAddress(cmd)) { emp ⇒
          persist(EmployeeAddressChanged(emp.id, emp.address.line1, emp.address.line2, emp.address.city, emp.address.stateOrProvince,
            emp.address.postalCode, emp.address.country, emp.version))(updateState)
        }
        case cmd: DeactivateEmployee => process(deactivate(cmd)) { emp ⇒
          persist(EmployeeDeactivated(emp.id, emp.version))(updateState)
        }
        case cmd: ActivateEmployee => process(activate(cmd)) { emp ⇒
          persist(EmployeeActivated(emp.id, emp.version))(updateState)
        }
        case cmd: TerminateEmployee => process(terminate(cmd)) { emp ⇒
          persist(EmployeeTerminated(emp.id, cmd.termDate, cmd.termReason, emp.version))(updateState)
        }
        case cmd: RehireEmployee => process(rehire(cmd)) { emp ⇒
          persist(EmployeeRehired(emp.id, cmd.rehireDate, emp.version))(updateState)
        }
        case cmd: RunPayroll =>
          readEmployees.values.filter(_.isInstanceOf[ActiveEmployee]).foreach { e =>
            persist(EmployeePaid(e.id, e.salary, e.version))(updateState)
          }
        case SnapshotEmployee(id) ⇒
          saveSnapshot(readEmployees.get(id).get)
        case Confirm(deliveryId) ⇒
          persist(MsgConfirmed(deliveryId)){evt =>
            confirmDelivery(deliveryId)
            context.system.eventStream.publish(evt)
          }
        case SaveSnapshotSuccess(snap) =>
          context.system.eventStream.publish(SnapshotConfirmed(snap))
      }
  }

  /**
   * Delivers the event to the destination.
   * @param evt event to deliver
   */
  def updateState(evt: EmployeeEvent): Unit = evt match {
    case e:EmployeeEvent ⇒ deliver(eventDestination, deliveryId ⇒ Msg(deliveryId, evt))
  }

  val receiveRecover: Receive = {
    case SnapshotOffer(metadata, offeredSnapshot) => updateEmployees(offeredSnapshot.asInstanceOf[Employee])
  }

  def hire(cmd: HireEmployee): DomainValidation[ActiveEmployee] =
    readEmployees.values.find(emp ⇒ emp.id == cmd.id) match {
      case Some(emp) ⇒ "EmployeeExists".failNel
      case None ⇒ ActiveEmployee.create(cmd.id, cmd.lastName, cmd.firstName, cmd.addressLine1, cmd.addressLine2, cmd.city, cmd.stateOrProvince,
        cmd.country, cmd.postalCode, cmd.startDate, cmd.dept, cmd.title, cmd.salary, -1L)
    }

  def changeLastName(cmd: ChangeEmployeeLastName): DomainValidation[ActiveEmployee] =
    updateActive(cmd.id, cmd.expectedVersion) { emp ⇒ emp.withLastName(cmd.lastName) }

  def changeFirstName(cmd: ChangeEmployeeFirstName): DomainValidation[ActiveEmployee] =
    updateActive(cmd.id, cmd.expectedVersion) { emp ⇒ emp.withFirstName(cmd.firstName) }

  def changeStartDate(cmd: ChangeEmployeeStartDate): DomainValidation[ActiveEmployee] =
    updateActive(cmd.id, cmd.expectedVersion) { emp ⇒ emp.withStartDate(cmd.startDate) }

  def changeAddress(cmd: ChangeEmployeeAddress): DomainValidation[Employee] =
    updateActive(cmd.id, cmd.expectedVersion) { emp ⇒ emp.withAddress(cmd.line1, cmd.line2, cmd.city, cmd.stateOrProvince, cmd.country, cmd.postalCode) }

  def changeDept(cmd: ChangeEmployeeDept): DomainValidation[ActiveEmployee] =
    updateActive(cmd.id, cmd.expectedVersion) { emp ⇒ emp.withDept(cmd.dept) }

  def changeTitle(cmd: ChangeEmployeeTitle): DomainValidation[ActiveEmployee] =
    updateActive(cmd.id, cmd.expectedVersion) { emp ⇒ emp.withTitle(cmd.title) }

  def changeSalary(cmd: ChangeEmployeeSalary): DomainValidation[ActiveEmployee] =
    updateActive(cmd.id, cmd.expectedVersion) { emp ⇒ emp.withSalary(cmd.salary) }

  def deactivate(cmd: DeactivateEmployee): DomainValidation[InactiveEmployee] =
    updateActive(cmd.id, cmd.expectedVersion) { emp ⇒ emp.leaveOfAbsence }

  def activate(cmd: ActivateEmployee): DomainValidation[ActiveEmployee] =
    updateInactive(cmd.id, cmd.expectedVersion) { emp ⇒ emp.activate }

  def terminate(cmd: TerminateEmployee): DomainValidation[Termination] =
    updateActive(cmd.id, cmd.expectedVersion) { emp ⇒ emp.terminate(cmd.termDate, cmd.termReason) }

  def rehire(cmd: RehireEmployee): DomainValidation[ActiveEmployee] =
    updateTermination(cmd.id, cmd.expectedVersion) { emp ⇒ emp.rehire(cmd.rehireDate) }

  def process(validation: DomainValidation[Employee])(onSuccess: Employee ⇒ Unit) {
    validation foreach { employee ⇒
      updateEmployees(employee)
      onSuccess(employee)
    }
    sender ! validation
  }

  def updateEmployee[B <: Employee](id: String, expectedVersion: Long)(f: Employee ⇒ DomainValidation[B]): DomainValidation[B] =
    readEmployees.get(id) match {
      case Some(emp) ⇒ for {
        current ← Employee.requireVersion(emp, expectedVersion)
        updated ← f(emp)
      } yield updated
      case None ⇒ s"employee $id does not exist".failNel
    }

  def updateActive[B <: Employee](id: String, version: Long)(f: ActiveEmployee ⇒ DomainValidation[B]): DomainValidation[B] =
    updateEmployee(id, version) {
      case emp: ActiveEmployee ⇒ f(emp)
      case emp: Employee ⇒ "EmployeeNotActive".failNel
    }

  def updateInactive[B <: Employee](id: String, version: Long)(f: InactiveEmployee ⇒ DomainValidation[B]): DomainValidation[B] =
    updateEmployee(id, version) {
      case emp: InactiveEmployee ⇒ f(emp)
      case emp: Employee ⇒ "EmployeeNotInactive".failNel
    }

  def updateTermination[B <: Employee](id: String, version: Long)(f: Termination ⇒ DomainValidation[B]): DomainValidation[B] =
    updateEmployee(id, version) {
      case emp: Termination  ⇒ f(emp)
      case emp: Employee ⇒ "EmployeeNotTerminated".failNel
    }

  private def updateEmployees(employee: Employee) {
    ref.single.transform(employees ⇒ employees + (employee.id -> employee))
  }

  private def readEmployees =
    ref.single.get
}

/**
 * This is the service in which all domain functionality is accessed.
 */
class EmployeeService(ref: Ref[Map[String, Employee]], processor: ActorRef)(implicit system: ActorSystem) {

  import system.dispatcher

  implicit val timeout = Timeout(5 seconds)

  def sendCommand(cmd: EmployeeCommand): Future[DomainValidation[Employee]] = processor ? cmd map (_.asInstanceOf[DomainValidation[Employee]])

  def getMap = ref.single.get

  def get(id: String): Option[Employee] = getMap.get(id)

  def getAll: Iterable[Employee] = getMap.values

  def getAllActive = getAll.filter(_.isInstanceOf[ActiveEmployee])

  def getAllInactive = getAll.filter(_.isInstanceOf[InactiveEmployee])

  def getAllTerminated = getAll.filter(_.isInstanceOf[Termination])
}
