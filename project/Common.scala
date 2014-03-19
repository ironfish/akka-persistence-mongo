/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
import sbt._
import Keys._

object Common {
  def Name = "akka-persistence-mongo"
  def NameCommon = Name + "-common"
  def NameCasbah = Name + "-casbah"
  def NameCommandSourcingExampleApp = Name + "-command-sourcing-example-app"
  def Organization = "com.github.ddevore"
  def ScalaVersion = "2.10.3"
  def Version = "0.6-SNAPSHOT"
  def ParallelExecutionInTest = false
  def ScalaCOptions = Seq( "-deprecation", "-unchecked", "-feature", "-language:postfixOps" )
  def TestCompile = "test->test;compile->compile"
  val PomXtra = {
    <url>https://github.com/ddevore/akka-persistence-mongo</url>
      <licenses>
        <license>
          <name>Apache 2</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        </license>
      </licenses>
      <scm>
        <connection>scm:git:github.com/ddevore/akka-persistence-mongo.git</connection>
        <developerConnection>scm:git:git@github.com:ddevore/akka-persistence-mongo.git</developerConnection>
        <url>github.com/ddevore/akka-persistence-mongo.git</url>
      </scm>
      <developers>
        <developer>
          <id>ddevore</id>
          <name>Duncan DeVore</name>
          <url>https://github.com/ddevore/</url>
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
