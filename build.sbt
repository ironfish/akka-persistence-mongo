organization := Common.Organization

name := "root"

scalaVersion := Common.ScalaVersion

version := Common.Version

parallelExecution in Test := Common.ParallelExecutionInTest

scalacOptions ++= Common.ScalaCOptions

publishLocal := {}

publish := {}

publishArtifact := false

lazy val root = (
  project in file(".")
    aggregate(akkaPersistenceMongoCommon, akkaPersistenceMongoCasbah, akkaPersistenceMongoCommandSourcingExampleApp)
  )

lazy val akkaPersistenceMongoCommon =
  project in file(Common.NameCommon)

lazy val akkaPersistenceMongoCasbah = (
  project in file(Common.NameCasbah)
    dependsOn(akkaPersistenceMongoCommon % Common.TestCompile)
  )

lazy val akkaPersistenceMongoCommandSourcingExampleApp =
  project in file(Common.NameCommandSourcingExampleApp)
