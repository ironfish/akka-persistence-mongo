/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package org.esexample

import akka.actor.Actor

@deprecated("This is no longer pertinent to the example but may be used in a future example.", since = "0.7.3")
class BenefitsDestination extends Actor {
import BenefitsProtocol._

  def receive = {
    case Msg(deliveryId, payload) => {
      payload match {
        case msg: BenefitsHired => handle(deliveryId, msg)
        case msg: BenefitsDeactivated => handle(deliveryId, msg)
        case msg: BenefitsActivated => handle(deliveryId, msg)
        case msg: BenefitsTerminated => handle(deliveryId, msg)
        case msg: BenefitsRehired => handle(deliveryId, msg)
      }
    }
  }

  def handle(deliveryId: Long, benefitsMessage: BenefitsMessage) = {
    context.system.eventStream.publish(benefitsMessage)
    sender ! Confirm(deliveryId)
  }
}
