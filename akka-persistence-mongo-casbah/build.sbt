import xerial.sbt.Sonatype._
import SonatypeKeys._

sonatypeSettings

organization := Common.Organization

profileName := Common.Organization

name := Common.NameCasbah

scalaVersion := Common.ScalaVersion

crossScalaVersions := Common.CrossScalaVersions

version := Common.Version

parallelExecution in Test := Common.ParallelExecutionInTest

scalacOptions ++= Common.ScalaCOptions

pomExtra := Common.PomXtra

resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"

resolvers += "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= Seq(
  "org.mongodb"         %% "casbah"                        % "2.7.3"              % "compile",
  "com.github.krasserm" %% "akka-persistence-testkit"      % "0.3.4"              % "test"
)
