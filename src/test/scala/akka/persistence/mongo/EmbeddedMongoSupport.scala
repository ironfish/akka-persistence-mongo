/**
  *  Copyright (C) 2015-2016 Duncan DeVore. <https://github.com/ironfish/>
  */
package akka.persistence.mongo

import de.flapdoodle.embed.mongo.{ Command, MongodStarter }
import de.flapdoodle.embed.mongo.config._
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.config.IRuntimeConfig
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.extract.UUIDTempNaming
import de.flapdoodle.embed.process.io.{ NullProcessor, Processors }
import de.flapdoodle.embed.process.io.directories.PlatformTempDir
import de.flapdoodle.embed.process.runtime.Network

object PortServer {
  lazy val freePort = Network.getFreeServerPort
}

import PortServer._

trait EmbeddedMongoSupport {

  lazy val host = "localhost"
  lazy val port = freePort
  lazy val localHostIPV6 = Network.localhostIsIPv6()

  val artifactStorePath = new PlatformTempDir()
  val executableNaming = new UUIDTempNaming()
  val command = Command.MongoD
  val version = Version.Main.PRODUCTION

  // Used to filter out console output messages.
  val processOutput = new ProcessOutput(
    Processors.named("[mongod>]", new NullProcessor),
    Processors.named("[MONGOD>]", new NullProcessor),
    Processors.named("[console>]", new NullProcessor))

  val runtimeConfig: IRuntimeConfig =
    new RuntimeConfigBuilder()
      .defaults(command)
      .processOutput(processOutput)
      .artifactStore(new ExtractedArtifactStoreBuilder()
        .defaults(command)
        .download(new DownloadConfigBuilder()
          .defaultsForCommand(command)
          .artifactStorePath(artifactStorePath).build())
        .executableNaming(executableNaming))
      .build()

  val mongodConfig =
    new MongodConfigBuilder()
      .version(version)
      .net(new Net(port, localHostIPV6))
      .cmdOptions(new MongoCmdOptionsBuilder()
        .syncDelay(1)
        .useNoPrealloc(false)
        .useSmallFiles(false)
        .useNoJournal(false)
        .enableTextSearch(true)
        .build())
      .build()

  lazy val mongodStarter = MongodStarter.getInstance(runtimeConfig)
  lazy val mongod = mongodStarter.prepare(mongodConfig)
  lazy val mongodExe = mongod.start()

  def embeddedMongoStartup() {
    mongodExe
  }

  def embeddedMongoShutdown() {
    mongod.stop()
    mongodExe.stop()
  }
}
