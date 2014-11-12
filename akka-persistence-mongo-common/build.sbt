import xerial.sbt.Sonatype._
import SonatypeKeys._

sonatypeSettings

organization := Common.Organization

profileName := Common.Organization

name := Common.NameCommon

scalaVersion := Common.ScalaVersion

crossScalaVersions := Common.CrossScalaVersions

version := Common.Version

parallelExecution in Test := Common.ParallelExecutionInTest

scalacOptions ++= Common.ScalaCOptions

pomExtra := Common.PomXtra

libraryDependencies ++= Seq(
  "ch.qos.logback"       % "logback-classic"                % "1.1.1"                        % "compile",
  "commons-io"           % "commons-io"                     % "2.4"                          % "test",
  "com.typesafe.akka"   %% "akka-testkit"                   % Common.AkkaVersion             % "test",
  "com.typesafe.akka"   %% "akka-persistence-experimental"  % Common.AkkaVersion             % "compile",
  "de.flapdoodle.embed"  % "de.flapdoodle.embed.mongo"      % Common.EmbeddedMongoVersion    % "test",
  "org.scalatest"       %% "scalatest"                      % Common.ScalatestVersion        % "test"
)
