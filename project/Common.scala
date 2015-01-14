/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <https://github.com/ironfish/>
 */
import sbt._
import Keys._

object Common {
  def Organization = "com.github.ironfish"
  def Name = "akka-persistence-mongo"
  def NameCommon = Name + "-common"
  def NameCasbah = Name + "-casbah"
  def AkkaVersion = "2.3.7"
  def CrossScalaVersions = Seq("2.10.4", "2.11.4")
  def EmbeddedMongoVersion = "1.46.1"
  def ScalaVersion = "2.11.4"
  def ScalatestVersion = "2.2.2"
  def Version = "0.7.6-SNAPSHOT"
  def ParallelExecutionInTest = false
  def ScalaCOptions = Seq( "-deprecation", "-unchecked", "-feature", "-language:postfixOps" )
  def TestCompile = "test->test;compile->compile"
  val PomXtra = {
    <url>https://github.com/ironfish/akka-persistence-mongo</url>
      <licenses>
        <license>
          <name>Apache 2</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        </license>
      </licenses>
      <scm>
        <connection>scm:git:github.com/ironfish/akka-persistence-mongo.git</connection>
        <developerConnection>scm:git:git@github.com:ironfish/akka-persistence-mongo.git</developerConnection>
        <url>github.com/ironfish/akka-persistence-mongo.git</url>
      </scm>
      <developers>
        <developer>
          <id>ironfish</id>
          <name>Duncan DeVore</name>
          <url>https://github.com/ironfish/</url>
        </developer>
        <developer>
          <id>sean-walsh</id>
          <name>Sean Walsh</name>
          <url>https://github.com/sean-walsh/</url>
        </developer>
        <developer>
          <id>aiacovella</id>
          <name>Al Iacovella</name>
          <url>https://github.com/aiacovella/</url>
        </developer>
      </developers>
  }
}
