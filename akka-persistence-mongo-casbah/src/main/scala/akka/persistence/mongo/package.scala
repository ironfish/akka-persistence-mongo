/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */

package akka.persistence

import com.mongodb.casbah.Imports._

import scala.language.implicitConversions

package object mongo {

import MongoPersistenceRoot._

  implicit def configCasbahWriteConcern(mwc: MongoWriteConcern): WriteConcern = mwc match {
    case a: Acknowledged         => new WriteConcern(1, mwc.timeout, false, false)
    case j: Journaled            => new WriteConcern(1, mwc.timeout, false, true)
    case r: ReplicasAcknowledged => new WriteConcern(2, mwc.timeout, false, false)
  }
}
