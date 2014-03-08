organization := Common.Organization

name := Common.NameSampleApp

scalaVersion := Common.ScalaVersion

version := "0.1-SNAPSHOT"

parallelExecution in Test := Common.ParallelExecutionInTest

scalacOptions ++= Common.ScalaCOptions

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= Seq(
  "com.typesafe.akka"  %% "akka-persistence-experimental"  % "2.3.0"        % "compile",
  "com.github.ddevore" %% "akka-persistence-mongo-casbah"  % "0.4-SNAPSHOT" % "compile",
  "org.scalaz"         %% "scalaz-core"                    % "7.0.5"        % "compile"
)
