/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package org.esexample

object BenefitsProtocol {

  /**
   * These are the sealed commands of every action that may be performed by an employee. Commands can be rejected.
   */
  sealed trait BenefitsMessage {
    def date: Long
  }

  final case class BenefitsHired(date: Long, data: String) extends BenefitsMessage
  final case class BenefitsDeactivated(date: Long, data: String) extends BenefitsMessage
  final case class BenefitsActivated(date: Long, data: String) extends BenefitsMessage
  final case class BenefitsTerminated(date: Long, data: String) extends BenefitsMessage
  final case class BenefitsRehired(date: Long, data: String) extends BenefitsMessage
}
