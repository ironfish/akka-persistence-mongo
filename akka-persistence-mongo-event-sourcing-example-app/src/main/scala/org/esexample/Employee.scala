/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package org.esexample

import akka.persistence.{PersistentActor, SnapshotOffer}

import org.joda.time.{DateTimeZone, DateTime}

import scalaz._
import Scalaz._

/**
 * An employee domain aggregate trait to be extended by ActiveEmployee, InactiveEmployee, etc.
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
  def salaryOwed: BigDecimal
}

/**
 * Companion object for employee abstraction that handles version validation.
 */
object Employee {
  import EmployeeProtocol._

  def requireVersion[A <: Employee](e: A, cmd: EmployeeCommand): DomainValidation[A] = {
    if (cmd.expectedVersion == e.version) e.successNel
    else s"$e version does not match $cmd version".failNel
  }
}

/**
 * Validations trait for employee abstractions.
 */
trait EmployeeValidations {
  case object IdRequired extends ValidationKey
  case object LastNameRequired extends ValidationKey
  case object FirstNameRequired extends ValidationKey
  case object DepartmentRequired extends ValidationKey
  case object TitleRequired extends ValidationKey
  case object TerminationReasonRequired extends ValidationKey

  def checkStartDate(d: Long): Validation[String, Long] = {
    val dt = new DateTime(d, DateTimeZone.UTC)
    if (dt.getMillis != dt.withTimeAtStartOfDay.getMillis) s"start date $d must be start of day boundary".failure else d.success
  }

  def checkSalary(s: BigDecimal): Validation[String, BigDecimal] =
    if (s < 0 || s > BigDecimal(1000000)) s"salary $s must be between 0 and 1000000.00".failure else s.success

  def checkDeactivateDate(dd: Long, sd: Long): Validation[String, Long] =
    if (dd <= sd) s"deactivate date $dd must be greater than start date $sd".failure else dd.success

  def checkActivateDate(sd: Long, dd: Long): Validation[String, Long] =
    if (sd <= dd) s"activate date $sd must be greater than deactivate date $dd".failure else sd.success

  def checkTerminationDate(td: Long, sd: Long): Validation[String, Long] =
    if (td <= sd) s"termination date $td must be greater than start date $sd".failure else td.success

  def checkRehireDate(rd: Long, td: Long): Validation[String, Long] =
    if (rd <= td) s"rehire date $td must be greater than termination date $td".failure else rd.success

  def checkPay(p: BigDecimal, so: BigDecimal): Validation[String, BigDecimal] =
    if(so - p < BigDecimal(0)) s"payment $p cannot be greater than salary owed $so".failure else p.success
}

/**
 * Case class that represents the state of an active employee.
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
  salary: BigDecimal,
  salaryOwed: BigDecimal) extends Employee with EmployeeValidations {
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

  def deactivate(deactivateDate: Long): DomainValidation[InactiveEmployee] =
    checkDeactivateDate(deactivateDate, this.startDate) fold (
      f => f.failNel,
      s => InactiveEmployee(this.id, this.version + 1, this.lastName, this.firstName, this.address, this.startDate, this.dept,
        this.title, this.salary, this.salaryOwed, s).success)

  def terminate(termDate: Long, termReason: String): DomainValidation[TerminatedEmployee] =
    (checkTerminationDate(termDate, this.startDate).toValidationNel |@|
      checkString(termReason, TerminationReasonRequired).toValidationNel) { (td, tr) =>
      TerminatedEmployee(this.id, this.version + 1, this.lastName, this.firstName, this.address, this.startDate, this.dept,
        this.title, this.salary, this.salaryOwed, td, tr)
    }

  def pay(p: BigDecimal): DomainValidation[ActiveEmployee] =
    checkPay(p, this.salaryOwed) fold (f => f.failNel, s => copy(version = version + 1, salaryOwed = salaryOwed - p).success)
}

/**
 * Companion object to ActiveEmployee for hiring.
 */
object ActiveEmployee extends EmployeeValidations {
  import EmployeeProtocol._
  import CommonValidations._

    def hire(cmd: HireEmployee): DomainValidation[ActiveEmployee] =
    (checkString(cmd.id, IdRequired).toValidationNel |@|
      0L.successNel |@|
      checkString(cmd.lastName, LastNameRequired).toValidationNel |@|
      checkString(cmd.firstName, FirstNameRequired).toValidationNel |@|
      Address.validate(cmd.street, cmd.city, cmd.stateOrProvince, cmd.country, cmd.postalCode) |@|
      checkStartDate(cmd.startDate).toValidationNel |@|
      checkString(cmd.dept, DepartmentRequired).toValidationNel |@|
      checkString(cmd.title, TitleRequired).toValidationNel |@|
      checkSalary(cmd.salary).toValidationNel) { (id, v, ln, fn, a, sd, d, t, s) =>
        ActiveEmployee(id, v, ln, fn, a, sd, d, t, s, s) }
}

/**
 * Case class that represents the state of an inactive employee.
 */
case class InactiveEmployee (
  id: String,
  version: Long,
  lastName: String,
  firstName: String,
  address: Address,
  startDate: Long,
  dept: String,
  title: String,
  salary: BigDecimal,
  salaryOwed: BigDecimal,
  deactivateDate: Long) extends Employee with EmployeeValidations {

  def activate(startDate: Long): DomainValidation[ActiveEmployee] =
    (checkStartDate(startDate).toValidationNel |@|
      checkActivateDate(startDate, this.deactivateDate).toValidationNel) {(s1, s2) =>
      ActiveEmployee(this.id, version = this.version + 1, this.lastName, this.firstName, this.address, s2, this.dept, this.title,
        this.salary, this.salaryOwed)
    }
}

/**
 * Case class that represents the state of a terminated employee.
 */
case class TerminatedEmployee (
  id: String,
  version: Long,
  lastName: String,
  firstName: String,
  address: Address,
  startDate: Long,
  dept: String,
  title: String,
  salary: BigDecimal,
  salaryOwed: BigDecimal,
  termDate: Long,
  termReason: String) extends Employee with EmployeeValidations {

    def rehire(rehireDate: Long): DomainValidation[ActiveEmployee] =
      checkRehireDate(rehireDate, this.termDate) fold (
        f => f.failNel,
        s => ActiveEmployee(this.id, version = this.version + 1, this.lastName, this.firstName, this.address, rehireDate,
          this.dept, this.title, this.salary, this.salaryOwed).success)
}

/**
 * Case class the contains the in-memory current state of all [[Employee]] aggregates.
 * @param employees Map[String, Employee] that contains the current state of all [[Employee]] aggregates.
 */
final case class EmployeeState(employees: Map[String, Employee] = Map.empty) {
  def update(e: Employee) = copy(employees = employees + (e.id -> e))
  def get(id: String) = employees.get(id)
  def getActive(id: String) = get(id) map (_.asInstanceOf[ActiveEmployee])
  def getActiveAll = employees map (_._2.asInstanceOf[ActiveEmployee])
  def getInactive(id: String) = get(id) map (_.asInstanceOf[InactiveEmployee])
  def getTerminated(id: String) = get(id) map (_.asInstanceOf[TerminatedEmployee])
}

/**
 * The EmployeeProcessor is responsible for maintaining  state changes for all [[Employee]] aggregates. This particular
 * processor uses Akka-Persistence's [[PersistentActor]]. It receives Commands and if valid will persist the generated events,
 * afterwhich it will updated the current state of the [[Employee]] being processed.
 */
class EmployeeProcessor extends PersistentActor {
  import EmployeeProtocol._

  override def persistenceId = "employee-persistence"

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
        evt.city, evt.stateOrProvince, evt.country, evt.postalCode), evt.startDate, evt.dept, evt.title, evt.salary, evt.salary))
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
    case evt: EmployeeDeactivated =>
      updateState(state.getActive(evt.id).map(e => InactiveEmployee(e.id, evt.version, e.lastName, e.firstName, e.address,
        e.startDate, e.dept, e.title, e.salary, e.salaryOwed, evt.deactivateDate)).get)
    case evt: EmployeeActivated =>
      updateState(state.getInactive(evt.id).map(e => ActiveEmployee(e.id, evt.version, e.lastName, e.firstName, e.address,
        evt.activateDate, e.dept, e.title, e.salary, e.salaryOwed)).get)
    case evt: EmployeeTerminated =>
      updateState(state.getActive(evt.id).map(e => TerminatedEmployee(e.id, evt.version, e.lastName, e.firstName, e.address,
      e.startDate, e.dept, e.title, e.salary, e.salaryOwed, evt.termDate, evt.termReason)).get)
    case evt: EmployeeRehired =>
      updateState(state.getTerminated(evt.id).map(e => ActiveEmployee(e.id, evt.version, e.lastName, e.firstName, e.address,
        evt.rehireDate, e.dept, e.title, e.salary, e.salaryOwed)).get)
    case evt: EmployeePaid =>
      updateState(state.getActive(evt.id).map(e => e.copy(version = evt.version, salaryOwed = e.salaryOwed - evt.amount)).get)
    case SnapshotOffer(_, snapshot: EmployeeState) => state = snapshot
  }

  /**
   * These are the commands that are requested. As command they can fail sending response back to the user. Each command will
   * generate one or more events to be journaled.
   */
  val receiveCommand: Receive = {
    case cmd: HireEmployee => hire(cmd) fold (
      f => sender ! ErrorMessage(s"error $f occurred on $cmd"),
      s => persist(EmployeeHired(s.id, s.version, s.lastName, s.firstName, s.address.street, s.address.city,
        s.address.stateOrProvince, s.address.postalCode, s.address.country, s.startDate, s.dept, s.title, s.salary)) { event =>
          updateState(s)
          context.system.eventStream.publish(event)
        })
    case cmd: ChangeEmployeeLastName => changeLastName(cmd) fold (
      f => sender ! ErrorMessage(s"error $f occurred on $cmd"),
      s => persist(EmployeeLastNameChanged(s.id, s.version, s.lastName)) { event =>
        updateState(s)
        context.system.eventStream.publish(event)
      })
    case cmd: ChangeEmployeeFirstName => changeFirstName(cmd) fold (
      f => sender ! ErrorMessage(s"error $f occurred on $cmd"),
      s => persist(EmployeeFirstNameChanged(s.id, s.version, s.firstName)) { event =>
        updateState(s)
        context.system.eventStream.publish(event)
      })
    case cmd: ChangeEmployeeAddress => changeAddress(cmd) fold (
      f => sender ! ErrorMessage(s"error $f occurred on $cmd"),
      s => persist(EmployeeAddressChanged(s.id, s.version, s.address.street, s.address.city, s.address.stateOrProvince,
        s.address.country, s.address.postalCode)) { event =>
          updateState(s)
          context.system.eventStream.publish(event)
      })
    case cmd: ChangeEmployeeStartDate => changeStartDate(cmd) fold (
      f => sender ! ErrorMessage(s"error $f occurred on $cmd"),
      s => persist(EmployeeStartDateChanged(s.id, s.version, s.startDate)) { event =>
        updateState(s)
        context.system.eventStream.publish(event)
      })
    case cmd: ChangeEmployeeDept => changeDept(cmd) fold (
      f => sender ! ErrorMessage(s"error $f occurred on $cmd"),
      s => persist(EmployeeDeptChanged(s.id, s.version, s.dept)) { event =>
        updateState(s)
        context.system.eventStream.publish(event)
      })
    case cmd: ChangeEmployeeTitle => changeTitle(cmd) fold (
      f => sender ! ErrorMessage(s"error $f occurred on $cmd"),
      s => persist(EmployeeTitleChanged(s.id, s.version, s.title)) { event =>
        updateState(s)
        context.system.eventStream.publish(event)
      })
    case cmd: ChangeEmployeeSalary => changeSalary(cmd) fold (
      f => sender ! ErrorMessage(s"error $f occurred on $cmd"),
      s => persist(EmployeeSalaryChanged(s.id, s.version, s.salary)) { event =>
        updateState(s)
        context.system.eventStream.publish(event)
      })
    case cmd: DeactivateEmployee => deactivate(cmd) fold (
      f => sender ! ErrorMessage(s"error $f occurred on $cmd"),
      s => persist(EmployeeDeactivated(s.id, s.version, s.deactivateDate)) { event =>
       updateState(s)
       context.system.eventStream.publish(event)
      })
    case cmd: ActivateEmployee => activate(cmd) fold (
      f => sender ! ErrorMessage(s"error $f occurred on $cmd"),
      s => persist(EmployeeActivated(s.id, s.version, s.startDate)) { event =>
        updateState(s)
        context.system.eventStream.publish(event)
      })
    case cmd: TerminateEmployee => terminate(cmd) fold (
      f => sender ! ErrorMessage(s"error $f occurred on $cmd"),
      s => persist(EmployeeTerminated(s.id, s.version, s.termDate, s.termReason)) { event =>
        updateState(s)
        context.system.eventStream.publish(event)
      })
    case cmd: RehireEmployee => rehire(cmd) fold (
      f => sender ! ErrorMessage(s"error $f occurred on $cmd"),
      s => persist(EmployeeRehired(s.id, s.version, s.startDate)) { event =>
        updateState(s)
        context.system.eventStream.publish(event)
      })
    case RunPayroll => state.getActiveAll map { e =>
      val cmd = PayEmployee(e.id, e.version, BigDecimal(5000))
      pay(cmd) fold(
        f => sender ! ErrorMessage(s"error $f occurred on $cmd"),
        s => persist(EmployeePaid(s.id, s.version, cmd.amount)) { event =>
          updateState(s)
          context.system.eventStream.publish(event)
        })}
    case cmd: GetEmployee   =>
      println(state.get(cmd.id))
      sender ! state.get(cmd.id)
    case SnapshotEmployees  => saveSnapshot(state)
    case "print"            => println("STATE: " + state)
  }

  def hire(cmd: HireEmployee): DomainValidation[ActiveEmployee] =
    state.get(cmd.id) match {
      case Some(emp) => s"employee for $cmd already exists".failNel
      case None      => ActiveEmployee.hire(cmd)
    }

  def changeLastName(cmd: ChangeEmployeeLastName): DomainValidation[ActiveEmployee] =
    updateActive(cmd) { e => e.withLastName(cmd.lastName) }

  def changeFirstName(cmd: ChangeEmployeeFirstName): DomainValidation[ActiveEmployee] =
    updateActive(cmd) { e => e.withFirstName(cmd.firstName) }

  def changeAddress(cmd: ChangeEmployeeAddress): DomainValidation[ActiveEmployee] =
    updateActive(cmd) { e => e.withAddress(cmd.street, cmd.city, cmd.stateOrProvince, cmd.country, cmd.postalCode) }

  def changeStartDate(cmd: ChangeEmployeeStartDate): DomainValidation[ActiveEmployee] =
    updateActive(cmd) { e => e.withStartDate(cmd.startDate) }

  def changeDept(cmd: ChangeEmployeeDept): DomainValidation[ActiveEmployee] =
    updateActive(cmd) { e => e.withDept(cmd.dept) }

  def changeTitle(cmd: ChangeEmployeeTitle): DomainValidation[ActiveEmployee] =
    updateActive(cmd) { e => e.withTitle(cmd.title) }

  def changeSalary(cmd: ChangeEmployeeSalary): DomainValidation[ActiveEmployee] =
    updateActive(cmd) { e => e.withSalary(cmd.salary) }

  def deactivate(cmd: DeactivateEmployee): DomainValidation[InactiveEmployee] =
    updateActive(cmd) { e => e.deactivate(cmd.deactivateDate) }

  def activate(cmd: ActivateEmployee): DomainValidation[ActiveEmployee] =
    updateInactive(cmd) { e => e.activate(cmd.activateDate) }

  def terminate(cmd: TerminateEmployee): DomainValidation[TerminatedEmployee] =
    updateActive(cmd) { e => e.terminate(cmd.termDate, cmd.termReason) }

  def rehire(cmd: RehireEmployee): DomainValidation[ActiveEmployee] =
    updateTerminated(cmd) { e => e.rehire(cmd.rehireDate) }

  def pay(cmd: PayEmployee): DomainValidation[ActiveEmployee] =
    updateActive(cmd) { e => e.pay(cmd.amount) }

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

  def updateInactive[A <: Employee](cmd: EmployeeCommand)(fn: InactiveEmployee => DomainValidation[A]): DomainValidation[A] =
    updateEmployee(cmd) {
      case emp: InactiveEmployee => fn(emp)
      case emp                   => s"$emp for $cmd is not inactive".failNel
    }

  def updateTerminated[A <: Employee](cmd: EmployeeCommand)(fn: TerminatedEmployee => DomainValidation[A]): DomainValidation[A] =
    updateEmployee(cmd) {
      case emp: TerminatedEmployee => fn(emp)
      case emp                     => s"$emp for $cmd is not terminated".failNel
    }
}
