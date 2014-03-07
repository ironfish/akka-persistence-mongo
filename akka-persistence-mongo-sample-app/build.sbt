organization := Common.Organization

name := Common.NameSampleApp

scalaVersion := Common.ScalaVersion

version := "0.1-SNAPSHOT"

parallelExecution in Test := Common.ParallelExecutionInTest

scalacOptions ++= Common.ScalaCOptions

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core" % "7.0.5" % "compile"
)
