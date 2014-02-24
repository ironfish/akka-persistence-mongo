name := "akka-persistence-mongo"

scalaVersion := "2.10.3"

lazy val baseSettings = Seq(
  version := "0.4-SNAPSHOT",
  organization := "com.github.ddevore"
)

def AkkaPersistenceMongoProject(name: String): Project = (
  Project(name, file(name))
  settings(baseSettings: _*)
  settings(scalacOptions ++= Seq( "-deprecation", "-unchecked", "-feature" ))
  settings(
    parallelExecution in Test := false,
    libraryDependencies ++= Seq(
      "com.typesafe.akka"   %% "akka-testkit"                  % "2.3.0-RC3" % "test",
      "org.scalatest"       %% "scalatest"                     % "2.0"       % "test",
      "commons-io"           % "commons-io"                    % "2.4"       % "test",
      "ch.qos.logback"       % "logback-classic"               % "1.1.1"     % "compile"
    )
  )
)

lazy val akkaPersistenceMongo = (
  Project("akka-persistence-mongo", file("."))
  settings(baseSettings: _*)
  settings(
    publish := { },
    publishLocal := { }
  )
  aggregate(akkaPersistenceMongoCommon, akkaPersistenceMongoCasbah)
)

lazy val akkaPersistenceMongoCommon = AkkaPersistenceMongoProject("akka-persistence-mongo-common")

lazy val akkaPersistenceMongoCasbah = (
  AkkaPersistenceMongoProject("akka-persistence-mongo-casbah")
  dependsOn(akkaPersistenceMongoCommon % "test->test;compile->compile")
)

lazy val akkaPersistenceMongoSampleApp = (
  AkkaPersistenceMongoProject("akka-persistence-mongo-sample-app")
  dependsOn(akkaPersistenceMongoCasbah % "test->test;compile->compile")
)
