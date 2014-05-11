/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package org.esexample

/**
 * This object contains all the commands and events as well as other messages that the employee aggregate may process.
 */
object EmployeeProtocol {

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
  final case class DeactivateEmployee(id: String, expectedVersion: Long, deactivateDate: Long) extends EmployeeCommand
  final case class ActivateEmployee(id: String, expectedVersion: Long, activateDate: Long) extends EmployeeCommand
  final case class TerminateEmployee(id: String, expectedVersion: Long, termDate: Long, termReason: String) extends EmployeeCommand
  final case class RehireEmployee(id: String, expectedVersion: Long, rehireDate: Long) extends EmployeeCommand
  final case class PayEmployee(id: String, expectedVersion: Long, amount: BigDecimal) extends EmployeeCommand

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
  final case class EmployeeDeactivated(id: String, version: Long, deactivateDate: Long) extends EmployeeEvent
  final case class EmployeeActivated(id: String, version: Long, activateDate: Long) extends EmployeeEvent
  final case class EmployeeTerminated(id: String, version: Long, termDate: Long, termReason: String)
  final case class EmployeeRehired(id: String, version: Long, rehireDate: Long) extends EmployeeEvent
  final case class EmployeePaid(id: String, version: Long, amount: BigDecimal) extends EmployeeEvent

  final case class ErrorMessage(data: String)

  case object RunPayroll
  case object SnapshotEmployees
}
