import xerial.sbt.Sonatype._
import SonatypeKeys._

sonatypeSettings

organization := Common.Organization

profileName := Common.Organization

name := Common.NameCasbah

scalaVersion := Common.ScalaVersion

version := Common.Version

parallelExecution in Test := Common.ParallelExecutionInTest

scalacOptions ++= Common.ScalaCOptions

pomExtra := Common.PomXtra

resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"

libraryDependencies ++= Seq(
  "org.mongodb"         %% "casbah"                        % "2.6.5"     % "compile",
  "com.github.krasserm" %% "akka-persistence-testkit"      % "0.2"       % "test"
)
