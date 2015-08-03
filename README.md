# An Akka Persistence Plugin for Mongo

[![Build Status](https://travis-ci.org/ironfish/akka-persistence-mongo.png?branch=master)](https://travis-ci.org/ironfish/akka-persistence-mongo)

A replicated [Akka Persistence](http://doc.akka.io/docs/akka/current/scala/persistence.html) journal backed by [MongoDB Casbah](http://mongodb.github.io/casbah/).

## Prerequisites

### Release

| Technology | Version                          |
| :--------: | -------------------------------- |
| Plugin     | 0.7.6                            |
| Scala      | 2.10.5, 2.11.7 - Cross Compiled  |
| Akka       | 2.3.12 or higher                 |
| Mongo      | 2.6.x or higher                  |

### Snapshot

| Technology | Version                          |
| :--------: | -------------------------------- |
| Plugin     | 0.7.7-SNAPSHOT                   |
| Scala      | 2.10.5, 2.11.7 - Cross Compiled  |
| Akka       | 2.3.12 or higher                 |
| Mongo      | 2.6.x or higher                  |

## Important Changes Starting with Version 0.7.5-SNAPSHOT

Due to the stability of the plugin and the increasing number of requests to publish to Maven Central Releases, we have implemented an official release strategy. In doing so, please **note** the following changes:

* Starting with version `0.7.5-SNAPSHOT` The organization name has **changed** from `com.github.ddevore` to `com.github.ironfish`.

* Versions `0.7.5` and beyond, when released, will be tagged and published to `https://oss.sonatype.org/content/repositories/releases`.

* Snapshots will continue to be published to `https://oss.sonatype.org/content/repositories/snapshots` and use the version moniker `#.#.#-SNAPSHOT`.

* When a snapshot is migrated to release, it will be tagged, published, and will **no** longer be available in the snapshots repository.

* If your looking or the [Sample Applications](https://github.com/ironfish/akka-persistence-mongo-samples), they have been moved to their own repo.

Just standard release management stuff. Nothing to see here move along. :-)

## Installation

### SBT

#### Release

```scala
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/releases"

libraryDependencies ++= Seq(
  "com.github.ironfish" %% "akka-persistence-mongo-casbah"  % "0.7.6" % "compile")
```

#### Snapshot

```scala
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= Seq(
  "com.github.ironfish" %% "akka-persistence-mongo-casbah"  % "0.7.7-SNAPSHOT" % "compile")
```

### Maven

#### Release

```XML
// Scala 2.10.5
<dependency>
    <groupId>com.github.ironfish</groupId>
    <artifactId>akka-persistence-mongo-casbah_2.10</artifactId>
    <version>0.7.6</version>
</dependency>

// Scala 2.11.7
<dependency>
    <groupId>com.github.ironfish</groupId>
    <artifactId>akka-persistence-mongo-casbah_2.11</artifactId>
    <version>0.7.6</version>
</dependency>
```

#### Snapshot

```XML
// Scala 2.10.5
<dependency>
    <groupId>com.github.ironfish</groupId>
    <artifactId>akka-persistence-mongo-casbah_2.10</artifactId>
    <version>0.7.7-SNAPSHOT</version>
</dependency>

// Scala 2.11.7
<dependency>
    <groupId>com.github.ironfish</groupId>
    <artifactId>akka-persistence-mongo-casbah_2.11</artifactId>
    <version>0.7.7-SNAPSHOT</version>
</dependency>
```

### Build Locally

You can build and install the plugin to your local Ivy cache. This requires sbt 0.13.8 or above.

```scala
sbt publishLocal
```

<br/>It can then be included as dependency:

```scala
libraryDependencies += "com.github.ironfish" %% "akka-persistence-mongo-casbah" % "0.7.7-SNAPSHOT"
```

## Mongo Specific Details

Both the [Journal](#journal-configuration) and [Snapshot](#snapshot-configuration) configurations use the following Mongo components for connection management and write guarantees.

### Mongo Connection String URI

The [Mongo Connection String URI Format](http://docs.mongodb.org/manual/reference/connection-string/) is used for establishing a connection to Mongo. Please **note** that while some of the components of the connection string are **[optional]** from a Mongo perspective, they are **[required]** for the Journal and Snapshot to function properly. Below are the required and optional components.

#### Standard URI connection scheme

```scala
mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database][.collection][?options]]
```

#### Required

| Component     | Description                                                                                 |
| :------------ | ------------------------------------------------------------------------------------------- |
| `mongodb:// ` | The prefix to identify that this is a string in the standard connection format.             |
| `host1`       | A server address to connect to. It is either a hostname, IP address, or UNIX domain socket. |
| `/database  ` | The name of the database to use.                                                            |
| `.collection` | The name of the collection to use.                                                          |

#### Optional

| Component | Description |
| :-------- | ----------- |
| `username:password@` | If specified, the client will attempt to log in to the specific database using these credentials after connecting to the mongod instance. |
| `:port1` | The default value is :27017 if not specified.                                               |
| `hostN` | You can specify as many hosts as necessary. You would specify multiple hosts, for example, for connections to replica sets. |
| `:portN` | The default value is :27017 if not specified. |
| `?options` | Connection specific options. See [Connection String Options](http://docs.mongodb.org/manual/reference/connection-string/#connections-connection-options) for a full description of these options. |

### Mongo Write Concern

[Write concern](http://docs.mongodb.org/manual/core/write-concern/) describes the guarantee that MongoDB provides when reporting on the success of a write operation. The strength of the write concerns determine the level of guarantee. The following write concerns are supported.

| Write Concern | Description |
| :------------ | ----------- |
| `acknowledged` | [Safe] - Exceptions are raised for network issues and server errors; waits on a server for the write operation. |
| `journaled` | [JournalSafe] - Exceptions are raised for network issues, and server errors; the write operation waits for the server to group commit to the journal file on disk. |
| `replicas-acknowledged` | [ReplicasSafe] - Exceptions are raised for network issues and server errors; waits for at least 2 servers for the write operation. |

## Journal Configuration

### Activation

To activate the journal feature of the plugin, add the following line to your Akka `application.conf`. This will run the journal with its default settings.

```scala
akka.persistence.journal.plugin = "casbah-journal"
```

### Connection

The default `mongo-journal-url` is a `string` with a value of:

```scala
casbah-journal.mongo-journal-url = "mongodb://localhost:27017/store.messages"
```

<br/>This value can be changed in the `application.conf` with the following key:

```scala
casbah-journal.mongo-journal-url

# Example
casbah-journal.mongo-journal-url = "mongodb://localhost:27017/employee.events"
```

<br/>See the [Mongo Connection String URI](#mongo-connection-string-uri) section of this document for more information.

### Write Concern

The default `mongo-journal-write-concern` is a `string` with a value of:

```scala
casbah-journal.mongo-journal-write-concern = "journaled"
```

<br/>This value can be changed in the `application.conf` with the following key:

```scala
casbah-journal.mongo-journal-write-concern

# Example
casbah-journal.mongo-journal-write-concern = "replicas-acknowledged"
```

### Write Concern Timeout

The default `mongo-journal-write-concern-timeout` is an `int` in milliseconds with a value of:

```scala
casbah-journal-mongo-journal-write-concern-timeout = 10000
```

<br/>This value can be changed in the `application.conf` with the following key:

```scala
casbah-journal-mongo-journal-write-concern-timeout

# Example
casbah-journal-mongo-journal-write-concern-timeout = 5000
```

<br/>See the [Mongo Write Concern](#mongo-write-concern) section of this document for more information.

## Snapshot Configuration

### Activation

To activate the snapshot feature of the plugin, add the following line to your Akka `application.conf`. This will run the snapshot-store with its default settings.

```scala
akka.persistence.snapshot-store.plugin = "casbah-snapshot-store"
```

### Connection

The default `mongo-snapshot-url` is a `string` with a value of:

```scala
casbah-snapshot-store.mongo-snapshot-url = "mongodb://localhost:27017/store.snapshots"
```

<br/>This value can be changed in the `application.conf` with the following key:

```scala
casbah-snapshot-store.mongo-snapshot-url

# Example
casbah-snapshot-store.mongo-snapshot-url = "mongodb://localhost:27017/employee.snapshots"
```

<br/>See the [Mongo Connection String URI](#mongo-connection-string-uri) section of this document for more information.

### Write Concern

The default `mongo-snapshot-write-concern` is a `string` with a value of:

```scala
casbah-snapshot-store.mongo-snapshot-write-concern = "journaled"
```

<br/>This value can be changed in the `application.conf` with the following key:

```scala
casbah-snapshot-store.mongo-snapshot-write-concern

# Example
casbah-snapshot-store.mongo-snapshot-write-concern = "replicas-acknowledged"
```

<br/>See the [Mongo Write Concern](#mongo-write-concern) section of this document for more information.

### Write Concern Timeout

The default `mongo-snapshot-write-concern-timeout` is an `int` in milliseconds with a value of:

```scala
casbah-snapshot-store.mongo-snapshot-write-concern-timeout = 10000
```

<br/>This value can be changed in the `application.conf` with the following key:

```scala
casbah-snapshot-store.mongo-snapshot-write-concern-timeout

# Example
casbah-snapshot-store.mongo-snapshot-write-concern-timeout = 5000
```

### Snapshot Load Attempts

The snapshot feature of the plugin allows for the selection of the youngest of `{n}` snapshots that match an upper bound specified by configuration. This helps where a snapshot may not have persisted correctly because of a JVM crash. As a result an attempt to load the snapshot may fail but an older may succeed.

The default `mongo-snapshot-load-attempt` is an `int` with a value of:

```scala
casbah-snapshot-store.mongo-snapshot-load-attempts = 3
```

<br/>This value can be changed in the `application.conf` with the following key:

```scala
casbah-snapshot-store.mongo-snapshot-load-attempts

# Example
casbah-snapshot-store.mongo-snapshot-load-attempts = 5
```

## Status

* All operations required by the Akka Persistence [journal plugin API](http://doc.akka.io/docs/akka/current/scala/persistence.html#journal-plugin-api) are supported.
* All operations required by the Akka Persistence [Snapshot store plugin API](http://doc.akka.io/docs/akka/current/scala/persistence.html#journal-plugin-api) are supported.
* Tested against [Akka Persistence Test Kit](https://github.com/krasserm/akka-persistence-testkit) version 0.3.4.
* Message writes are batched to optimize throughput.
* `AtLeastOnceDelivery` writes are batched to optimize throughput.
* Mongo Sharding is not yet supported.
* Akka-Persistence is still considered **experimental** and as such the underlying api may change based on changes to Akka Persistence or user feedback.

## Performance

Minimal performance testing is included against a **native** instance. In general the journal will persist around 8,000 to 10,000 messages per second.

## Sample Applications

The [sample applications](https://github.com/ironfish/akka-persistence-mongo-samples) are now located in their own repository.

## Change Log

### 0.7.6

* Upgrade `sbt` to 0.13.8.
* Upgrade `Scala` cross-compilation to 2.10.5 & 2.11.7.
* Upgrade `Akka` to 2.3.12.

### 0.7.5

* Upgrade `sbt` to 0.13.7.
* Upgrade `Scala` cross-compilation to 2.10.4 & 2.11.4.
* Upgrade `Akka` to 2.3.7.
* Examples moved to their own [repository](https://github.com/ironfish/akka-persistence-mongo-samples).
* Removed `logback.xml` in `akka-persistence-mongo-casbah` as it was not needed.
* Added `pomOnly()` resolution to `casbah` dependency, fixes #63.

### 0.7.4

* First release version to Maven Central Releases.
* Upgrade `Sbt` to 0.13.5.
* Upgrade `Scala` cross-compilation to 2.10.4 & 2.11.2.
* Upgrade `Akka` to 2.3.5.
* Added exception if `/database` or `.collection` are not accessible upon boot. Thanks @Fristi.
* Modified snapshot feature for custom serialization support. Thanks @remcobeckers.

### 0.7.3-SNAPSHOT

* Upgrade `Sbt` to 0.13.4.
* Upgrade `Scala` cross-compilation to 2.10.4 & 2.11.2.
* Upgrade `Akka` to 2.3.4.
* `@deprecated` write confirmations, `CasbahJournal.writeConfirmations`, in favor of `AtLeastOnceDelivery`.
* `@deprecated` delete messages, `CasbahJournal.deleteMessages`, per akka-persistence documentation.

## Author / Maintainer

* [Duncan DeVore (@ironfish)](https://github.com/ironfish/)

## Contributors

* [Heiko Seeberger (@hseeberger)](https://github.com/hseeberger)
* [Sean Walsh (@SeanWalshEsq)](https://github.com/sean-walsh/)
* [Al Iacovella](https://github.com/aiacovella/)
* [Remco Beckers](https://github.com/remcobeckers)
* [Mark Fristi](https://github.com/Fristi)
