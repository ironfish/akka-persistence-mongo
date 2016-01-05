package akka.persistence.mongo

import java.util.concurrent.TimeUnit

import com.mongodb.casbah.Imports._
import com.typesafe.config.{Config, ConfigException, ConfigFactory}
import org.scalatest.{Matchers, WordSpecLike}

object CasbahRootSpec {

  val wOptionZero = 0
  val wOptionOne = 1
  val wOptionMajority = "majority"
  val wOptionFalse = false

  val wTimeoutOneThousand = 10000
  val wTimeoutString = "timeout"
}

class CasbahRootSpec extends WordSpecLike with Matchers {

  import CasbahRoot._
  import CasbahRootSpec._

  "A CasbahRoot" should {
    "Return a valid WriteConcern with a valid Integer woption and wtimeout" in {
      val config = ConfigFactory.parseString(
        s"""
           |$wOptionKey = $wOptionOne
           |$wTimeoutKey = $wTimeoutOneThousand
     """.stripMargin
      )
      val writeConcern: WriteConcern = CasbahRoot.writeConcern(config)
      writeConcern.getW shouldBe wOptionOne
      writeConcern.getWTimeout(TimeUnit.MILLISECONDS) shouldBe wTimeoutOneThousand
    }
    "throw ConfigException.BadValue when woption is zero" in {
      val config = ConfigFactory.parseString(
        s"""
           |$wOptionKey = $wOptionZero
           |$wTimeoutKey = $wTimeoutOneThousand
     """.stripMargin
      )
      val thrown: ConfigException.BadValue = the [ConfigException.BadValue] thrownBy CasbahRoot.writeConcern(config)
      thrown.getMessage should include (s"$wOptionBadValueSuffix")
    }
    "Return a valid WriteConcern with a valid String woption and wtimeout" in {
      val config = ConfigFactory.parseString(
        s"""
           |$wOptionKey = $wOptionMajority
           |$wTimeoutKey = $wTimeoutOneThousand
     """.stripMargin
      )
      val writeConcern: WriteConcern = CasbahRoot.writeConcern(config)
      writeConcern.getWString shouldBe wOptionMajority
      writeConcern.getWTimeout(TimeUnit.MILLISECONDS) shouldBe wTimeoutOneThousand
    }
    "throw ConfigException.WrongType when woption not of type Integer or String" in {
      val config = ConfigFactory.parseString(
        s"""
           |$wOptionKey = $wOptionFalse
           |$wTimeoutKey = $wTimeoutOneThousand
     """.stripMargin
      )
      val thrown: ConfigException.WrongType = the [ConfigException.WrongType] thrownBy CasbahRoot.writeConcern(config)
      thrown.getMessage should include (s"$wOptionWrongTypeSuffix")
    }
    "throw ConfigException.WrongType when wtimeout not of type Long" in {
      val config = ConfigFactory.parseString(
        s"""
           |$wOptionKey = $wOptionOne
           |$wTimeoutKey = $wTimeoutString
     """.stripMargin
      )
      val thrown: ConfigException.WrongType = the [ConfigException.WrongType] thrownBy CasbahRoot.writeConcern(config)
      thrown.getMessage should include (s"$wTimeoutKey has type STRING rather than NUMBER")
    }
  }
}
