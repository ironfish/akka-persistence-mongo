/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */

package akka.persistence

import com.mongodb.casbah.Imports._

import scala.language.implicitConversions

package object mongo {

import MongoPersistenceRoot._

  implicit def configCasbahWriteConcern(mwc: MongoWriteConcern): WriteConcern = mwc match {
    case Acknowledged => WriteConcern.Safe
    case Journaled => WriteConcern.JournalSafe
    case ReplicasAcknowledged => WriteConcern.ReplicasSafe
  }
}
