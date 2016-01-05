val akkaVer = "2.4.1"
val casbahVer = "3.1.0"
val commonsIoVer = "2.4"
val embeddedMongoVer = "1.50.1"
val logbackVer = "1.1.3"
val scalaVer = "2.11.7"
val scalatestVer = "2.2.4"

organization := "com.github.ironfish"
name := "akka-persistence-mongo"
version := "1.0.0-SNAPSHOT"

scalaVersion := scalaVer
scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-deprecation",
  "-unchecked",
  "-feature",
  "-language:postfixOps",
  "-target:jvm-1.8")

parallelExecution in ThisBuild := false

parallelExecution in Test := false
logBuffered in Test := false

libraryDependencies ++= Seq(
  "ch.qos.logback"       % "logback-classic"             % logbackVer         % "test",
  "commons-io"           % "commons-io"                  % commonsIoVer       % "test",
  "com.typesafe.akka"   %% "akka-testkit"                % akkaVer            % "test",
  "com.typesafe.akka"   %% "akka-persistence"            % akkaVer            % "compile",
  "com.typesafe.akka"   %% "akka-persistence-tck"        % akkaVer            % "test",
  "de.flapdoodle.embed"  % "de.flapdoodle.embed.mongo"   % embeddedMongoVer   % "test",
  "org.mongodb"         %% "casbah"                      % casbahVer          % "compile" pomOnly(),
  "org.scalatest"       %% "scalatest"                   % scalatestVer       % "test"
)

pomExtra := {
  <url>https://github.com/ironfish/akka-persistence-mongo</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:github.com/ironfish/akka-persistence-mongo.git</connection>
      <developerConnection>scm:git:git@github.com:ironfish/akka-persistence-mongo.git</developerConnection>
      <url>github.com/ironfish/akka-persistence-mongo.git</url>
    </scm>
    <developers>
      <developer>
        <id>ironfish</id>
        <name>Duncan DeVore</name>
        <url>https://github.com/ironfish/</url>
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
