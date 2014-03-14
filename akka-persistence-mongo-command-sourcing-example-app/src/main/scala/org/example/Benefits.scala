package org.example

import akka.persistence.ConfirmablePersistent
import akka.actor.Actor
import com.novus.salat._
import com.novus.salat.global._
import com.mongodb.casbah.Imports._

sealed trait BenefitsMessages
case class EmployeeBenefits(employeeId: String, startDate: Long, termDates: List[Long], rehireDates: List[Long]) extends BenefitsMessages

/**
 * This read side representation is interested in maintaining a true employment duration, which takes into account periods of
 * non-employment. We could get fancy here and schedule updates to an employmentLength field if we wanted but for the sake of
 * this sample we'll just accumulate the pertinent dates.
 */
class Benefits(mongoHost: String, mongoPort: Int) extends Actor {

  implicit val writeConcern = WriteConcern.JournalSafe

  def receive: Actor.Receive = {
    case p @ ConfirmablePersistent(event, sequenceNr, redeliveries) =>
      val con = MongoConnection(mongoHost, mongoPort)
      val col = con("hr")("benefits")

      event match {
        case cmd: EmployeeHired      =>
          val eb = EmployeeBenefits(cmd.id, cmd.startDate, Nil, Nil)
          val dbo = grater[EmployeeBenefits].asDBObject(eb)
          col.insert(dbo)
          p.confirm()

        case cmd: EmployeeTerminated =>
          val dbo = col.findOne(MongoDBObject("employeeId" -> cmd.id)).get
          val eb = grater[EmployeeBenefits].asObject(dbo)
          val up = eb.copy(termDates = eb.termDates :+ cmd.termDate)
          col.update(MongoDBObject("employeeId" -> cmd.id), grater[EmployeeBenefits].asDBObject(up))
          p.confirm()

        case cmd: EmployeeRehired    =>
          val dbo = col.findOne(MongoDBObject("employeeId" -> cmd.id)).get
          val eb = grater[EmployeeBenefits].asObject(dbo)
          val up = eb.copy(rehireDates = eb.rehireDates :+ cmd.rehireDate)
          col.update(MongoDBObject("employeeId" -> cmd.id), grater[EmployeeBenefits].asDBObject(up))
          p.confirm()

        case _ => // Not interested
      }
  }

  /**
   * In the waiting state we ensure ordering in the database so that creates happen before updates, etc.
   */
  def waiting: Actor.Receive = {
    case _ => println("waiting state. No messages expected in the waiting state!")
  }
}
