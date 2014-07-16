package org.example

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
    case Msg(deliveryId, payload) =>
      val con = MongoClient(mongoHost, mongoPort)
      val col = con("hr")("benefits")

      payload match {
        case cmd: EmployeeHired      =>
          val eb = EmployeeBenefits(cmd.id, cmd.startDate, Nil, Nil)
          val dbo = grater[EmployeeBenefits].asDBObject(eb)
          col.insert(dbo)
          sender() ! Confirm(deliveryId)

        case cmd: EmployeeTerminated =>
          val dbo = col.findOne(MongoDBObject("employeeId" -> cmd.id)).get
          val eb = grater[EmployeeBenefits].asObject(dbo)
          val up = eb.copy(termDates = eb.termDates :+ cmd.termDate)
          col.update(MongoDBObject("employeeId" -> cmd.id), grater[EmployeeBenefits].asDBObject(up))
          sender() ! Confirm(deliveryId)

        case cmd: EmployeeRehired    =>
          val dbo = col.findOne(MongoDBObject("employeeId" -> cmd.id)).get
          val eb = grater[EmployeeBenefits].asObject(dbo)
          val up = eb.copy(rehireDates = eb.rehireDates :+ cmd.rehireDate)
          col.update(MongoDBObject("employeeId" -> cmd.id), grater[EmployeeBenefits].asDBObject(up))
          sender() ! Confirm(deliveryId)

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
