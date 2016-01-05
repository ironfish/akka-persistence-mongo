/**
  *  Copyright (C) 2015-2016 Duncan DeVore. <https://github.com/ironfish/>
  */
package akka.persistence.mongo

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, ActorLogging}
import akka.serialization.{Serialization, SerializationExtension}
import com.mongodb.casbah.Imports._
import com.typesafe.config.{Config, ConfigException}

import scala.reflect.ClassTag
import scala.reflect.classTag
import scala.util.{Failure, Try}

object CasbahRoot {
  val wOptionKey: String = "woption"
  val wTimeoutKey: String = "wtimeout"
  val wOptionWrongTypeSuffix: String = s"$wOptionKey has type INVALID rather than Integer or String."
  val wOptionBadValueSuffix: String = s"must be greater than zero."

  def writeConcern(config: Config): WriteConcern = {
    val wOption = config.getAnyRef(wOptionKey)
    val wTimeout = config.getLong(wTimeoutKey)
    wOption match {
      case i: Integer if i == 0 =>
        throw new ConfigException.BadValue(config.origin, s"$wOptionKey", s"$wOptionBadValueSuffix")
      case i: Integer =>
        WriteConcern.Journaled.withW(i).withWTimeout(wTimeout, TimeUnit.MILLISECONDS)
      case s: String =>
        WriteConcern.Journaled.withW(s).withWTimeout(wTimeout, TimeUnit.MILLISECONDS)
      case _ =>
        throw new ConfigException.WrongType(config.origin, s"$wOptionWrongTypeSuffix")
    }
  }
}

trait CasbahRoot extends CasbahCommon { mixin : ActorLogging =>
  private val uniqueKey = "unique"

  val actorSystem: ActorSystem

  protected lazy val serialization: Serialization = SerializationExtension(actorSystem)

  protected lazy val indexOptions: MongoDBObject = MongoDBObject(uniqueKey -> true)

  implicit def writeConcern: WriteConcern = CasbahRoot.writeConcern(config)

  protected def initialize(): Unit

  protected def fromBytes[T: ClassTag](dbObject: DBObject, key: String): Try[T] = {
    try {
      val byteArray: Array[Byte] = dbObject.as[Array[Byte]](key)
      serialization.deserialize(byteArray, classTag[T].runtimeClass.asInstanceOf[Class[T]])
    } catch {
      case e: Exception =>
        Failure(e)
    }
  }

  protected def toBytes(data: AnyRef): Try[Array[Byte]] = serialization.serialize(data)

  private val errorHandler: PartialFunction[Throwable, Unit] = {
    case ex: Exception => log.info("Index creation error: {}", ex.getMessage)
  }

  def ensure(ind0: DBObject, ind1: DBObject): (MongoCollection) => Unit =
    collection =>
      Try(collection.createIndex(ind0, ind1)).recover(errorHandler)


  def ensure(ind: DBObject): (MongoCollection) => Unit =
    collection =>
      Try (collection.createIndex(ind)).recover(errorHandler)

  protected def shutdown(): Unit = {
    client.close()
  }
}
