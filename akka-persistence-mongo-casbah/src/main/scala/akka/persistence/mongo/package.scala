/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <https://github.com/ironfish/>
 */

package akka.persistence

import akka.actor.ActorLogging
import com.mongodb.casbah.Imports._

import scala.language.implicitConversions
import scala.util.Try

package object mongo {

import MongoPersistenceRoot._

  implicit def configCasbahWriteConcern(mwc: MongoWriteConcern): WriteConcern = mwc match {
    case a: Acknowledged         => new WriteConcern(1, mwc.timeout, false, false)
    case j: Journaled            => new WriteConcern(1, mwc.timeout, false, true)
    case r: ReplicasAcknowledged => new WriteConcern(2, mwc.timeout, false, false)
  }

  trait IndexesSupport {
    mixin : ActorLogging =>

    private val errorHandler: PartialFunction[Throwable, Unit] = {
      case ex: Exception => log.info("Index creation error: {}", ex.getMessage)
    }

    def ensure(ind0: DBObject, ind1: DBObject): (MongoCollection) => Unit =
      collection =>
        Try(collection.createIndex(ind0, ind1)).recover(errorHandler)


    def ensure(ind: DBObject): (MongoCollection) => Unit =
      collection =>
        Try (collection.createIndex(ind)).recover(errorHandler)

  }
}
