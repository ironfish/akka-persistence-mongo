/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package org.esexample

import scala.concurrent.duration._

import akka.actor._
import akka.persistence._

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._

sealed trait Benefits {
  def employeeId: String
}

case class BenefitDates(
  employeeId: String,
  startDate: Long,
  deactivateDates: List[Long],
  termDates: List[Long],
  rehireDates: List[Long]) extends Benefits

class BenefitsView extends View {
  import EmployeeProtocol._
  import BenefitsProtocol._
  implicit val writeConcern = WriteConcern.JournalSafe

  def config = context.system.settings.config.getConfig("benefits-view")

  private val uri = MongoClientURI(config.getString("mongo-url"))
  private val client =  MongoClient(uri)
  private val db = client(uri.database.get)
  private val coll = db(uri.collection.get)

  override def processorId = "employee-processor"
  override def viewId = "benefits-view"

  def receive = {
    case p @ Persistent(payload, _) =>
      payload match {
        case evt: EmployeeHired =>
          val eb = BenefitDates(evt.id, evt.startDate, Nil, Nil, Nil)
          val dbo = grater[BenefitDates].asDBObject(eb)
          coll.insert(dbo)
        case evt: EmployeeDeactivated =>
          val dbo = coll.findOne(MongoDBObject("employeeId" -> evt.id)).get
          val eb = grater[BenefitDates].asObject(dbo)
          val up = eb.copy(deactivateDates = eb.deactivateDates :+ evt.deactivateDate)
          coll.update(MongoDBObject("employeeId" -> evt.id), grater[BenefitDates].asDBObject(up))
        case evt: EmployeeActivated =>
          val dbo = coll.findOne(MongoDBObject("employeeId" -> evt.id)).get
          val eb = grater[BenefitDates].asObject(dbo)
          val up = eb.copy(startDate = evt.activateDate)
          coll.update(MongoDBObject("employeeId" -> evt.id), grater[BenefitDates].asDBObject(up))
        case evt: EmployeeTerminated =>
          val dbo = coll.findOne(MongoDBObject("employeeId" -> evt.id)).get
          val eb = grater[BenefitDates].asObject(dbo)
          val up = eb.copy(termDates = eb.termDates :+ evt.termDate)
          coll.update(MongoDBObject("employeeId" -> evt.id), grater[BenefitDates].asDBObject(up))
        case evt: EmployeeRehired =>
          val dbo = coll.findOne(MongoDBObject("employeeId" -> evt.id)).get
          val eb = grater[BenefitDates].asObject(dbo)
          val up = eb.copy(rehireDates = eb.rehireDates :+ evt.rehireDate)
          coll.update(MongoDBObject("employeeId" -> evt.id), grater[BenefitDates].asDBObject(up))
        case _ => // do nothing
      }
  }

  override def postStop(): Unit = {
    client.close()
    super.postStop()
  }
}
