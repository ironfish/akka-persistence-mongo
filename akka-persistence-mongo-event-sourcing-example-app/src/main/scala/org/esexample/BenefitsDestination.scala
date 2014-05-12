/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package org.esexample

import akka.actor.Actor
import akka.persistence.ConfirmablePersistent

class BenefitsDestination extends Actor {
import BenefitsProtocol._

  def receive = {
    case cp @ ConfirmablePersistent(payload, sequenceNr, _) =>
      payload match {
        case msg: BenefitsHired =>
          context.system.eventStream.publish(msg)
          // println(s"destination received ${payload} (sequence nr = ${sequenceNr})")
          cp.confirm()
        case msg: BenefitsDeactivated =>
          context.system.eventStream.publish(msg)
          // println(s"destination received ${payload} (sequence nr = ${sequenceNr})")
          cp.confirm()
        case msg: BenefitsActivated =>
          context.system.eventStream.publish(msg)
          // println(s"destination received ${payload} (sequence nr = ${sequenceNr})")
          cp.confirm()
        case msg: BenefitsTerminated =>
          context.system.eventStream.publish(msg)
          // println(s"destination received ${payload} (sequence nr = ${sequenceNr})")
          cp.confirm()
        case msg: BenefitsRehired =>
          context.system.eventStream.publish(msg)
          // println(s"destination received ${payload} (sequence nr = ${sequenceNr})")
          cp.confirm()
      }
  }
}
