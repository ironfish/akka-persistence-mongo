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
 *
 * This actor shows how to do upsert to a database when you want to ensure ordering, i.e. the creation is guaranteed to
 * occur before any updates.  This is done using akka become and then unbecome after completion of the future mongodb
 * write operation.  Any write operation that fails for any reason will cause an exception and a restart of this actor in
 * the normal state.
 */
class Benefits(mongoHost: String, mongoPort: Int) extends Actor {

  def receive: Actor.Receive = {
    case p @ ConfirmablePersistent(event, sequenceNr, redeliveries) =>
      val con = MongoConnection(mongoHost, mongoPort)
      val col = con("hr")("benefits")

      event match {
        case cmd: EmployeeHired      =>
          context become waiting
          val eb = EmployeeBenefits(cmd.id, cmd.startDate, Nil, Nil)
          val dbo = grater[EmployeeBenefits].asDBObject(eb)
          col.insert(dbo)
          p.confirm()
          context.unbecome()

        case cmd: EmployeeTerminated =>
          context become waiting
          val dbo = col.findOne(MongoDBObject("employeeId" -> cmd.id)).get
          val eb = grater[EmployeeBenefits].asObject(dbo)
          val up = eb.copy(termDates = eb.termDates :+ cmd.termDate)
          col.update(MongoDBObject("employeeId" -> cmd.id), grater[EmployeeBenefits].asDBObject(up))
          p.confirm()
          context.unbecome()

        case cmd: EmployeeRehired    =>
          context become waiting
          val dbo = col.findOne(MongoDBObject("employeeId" -> cmd.id)).get
          val eb = grater[EmployeeBenefits].asObject(dbo)
          val up = eb.copy(rehireDates = eb.rehireDates :+ cmd.rehireDate)
          col.update(MongoDBObject("employeeId" -> cmd.id), grater[EmployeeBenefits].asDBObject(up))
          p.confirm()
          context.unbecome()

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
