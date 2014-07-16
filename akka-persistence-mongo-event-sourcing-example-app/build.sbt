organization := Common.Organization

name := Common.NameEventSourcingExampleApp

scalaVersion := Common.ScalaVersion

version := "0.1-SNAPSHOT"

parallelExecution in Test := Common.ParallelExecutionInTest

scalacOptions ++= Common.ScalaCOptions

publishLocal := {}

publish := {}

publishArtifact := false

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= Seq(
  "com.typesafe.akka"      %% "akka-persistence-experimental" % "2.3.4"            % "compile",
  "com.github.ddevore"     %% "akka-persistence-mongo-casbah" % Common.Version     % "compile",
  "org.scalaz"             %% "scalaz-core"                   % "7.0.6"            % "compile",
  "com.novus"              %% "salat"                         % "1.9.8"            % "compile",
  "org.scala-stm"          %% "scala-stm"                     % "0.7"              % "compile",
  "com.typesafe.akka"      %% "akka-slf4j"                    % "2.3.4"            % "compile",
  "com.typesafe.akka"      %% "akka-testkit"                  % "2.3.4"            % "test",
  "de.flapdoodle.embed"     % "de.flapdoodle.embed.mongo"     % "1.43"             % "test",
  "org.scalatest"          %% "scalatest"                     % "2.1.3"            % "test"
)
