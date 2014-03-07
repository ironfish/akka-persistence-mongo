organization := Common.Organization

name := "root"

scalaVersion := Common.ScalaVersion

version := Common.Version

parallelExecution in Test := Common.ParallelExecutionInTest

scalacOptions ++= Common.ScalaCOptions

packagedArtifacts in file(".") := Map.empty // don't publish root project

lazy val root = (
  project in file(".")
  aggregate(akkaPersistenceMongoCommon, akkaPersistenceMongoCasbah)
)

lazy val akkaPersistenceMongoCommon =
  project in file(Common.NameCommon)

lazy val akkaPersistenceMongoCasbah = (
  project in file(Common.NameCasbah)
  dependsOn(akkaPersistenceMongoCommon % Common.TestCompile)
)

lazy val akkaPersistenceMongoSampleApp = (
  project in file(Common.NameSampleApp)
  dependsOn(akkaPersistenceMongoCasbah % Common.TestCompile)
)
