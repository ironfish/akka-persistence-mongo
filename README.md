# An Akka Persistence Plugin for Mongo

[![Build Status](https://travis-ci.org/ironfish/akka-persistence-mongo.png?branch=master)](https://travis-ci.org/ironfish/akka-persistence-mongo)

A replicated [Akka Persistence](http://doc.akka.io/docs/akka/current/scala/persistence.html) journal backed by [MongoDB Casbah](http://mongodb.github.io/casbah/).

## Prerequisites

### Release

| Technology | Version                          |
| :--------: | -------------------------------- |
| Plugin     | [<img src="https://img.shields.io/maven-central/v/com.github.ironfish/akka-persistence-mongo-casbah_2.11.svg?label=latest%20release%20for%202.11"/>](http://search.maven.org/#search%7cga%7c1%7cg%3a%22com.github.ironfish%22a%3a%22akka-persistence-mongo-casbah_2.11%22)<br/>[<img src="https://img.shields.io/maven-central/v/com.github.ironfish/akka-persistence-mongo-casbah_2.10*.svg?label=latest%20release%20for%202.10"/>](http://search.maven.org/#search%7cga%7c1%7cg%3a%22com.github.ironfish%22a%3a%22akka-persistence-mongo-casbah_2.10%22)|
| Scala      | 2.10.5, 2.11.7 - Cross Compiled  |
| Akka       | 2.3.12 or higher                 |
| Mongo      | 2.6.x or higher                  |

### Snapshot

| Technology | Version                          |
| :--------: | -------------------------------- |
| Plugin     | [<img src="https://img.shields.io/badge/latest%20snapshot%20for%202.11-1.0.0--SNAPSHOT-blue.svg"/>](https://oss.sonatype.org/content/repositories/snapshots/com/github/ironfish/akka-persistence-mongo-casbah_2.11/1.0.0-SNAPSHOT/)
| Scala      | 2.11.7                           |
| Akka       | 2.4.1 or higher                  |
| Mongo      | 3.1.x or higher                  |

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
  "com.github.ironfish" %% "akka-persistence-mongo"  % "1.0.0-SNAPSHOT" % "compile")
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
// Scala 2.11.7
<dependency>
    <groupId>com.github.ironfish</groupId>
    <artifactId>akka-persistence-mongo_2.11</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Build Locally

You can build and install the plugin to your local Ivy cache. This requires sbt 0.13.8 or above.

```scala
sbt publishLocal
```

<br/>It can then be included as dependency:

```scala
libraryDependencies += "com.github.ironfish" %% "akka-persistence-mongo" % "1.0.0-SNAPSHOT"
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

The [Write Concern Specification](https://docs.mongodb.org/manual/reference/write-concern/) describes the guarantee that MongoDB provides when reporting on the success of a write operation. The strength of the write concern determine the level of guarantee. When a `PersistentActor's` `persist` or `persistAsync` method completes successfully, a plugin must ensure the `message` or `snapshot` has persisted to the store. As a result, this plugin implementation enforces Mongo [Journaling](https://docs.mongodb.org/manual/core/journaling/) on all write concerns and requires all mongo instance(s) to enable journaling.

| Options | Description |
| :------------ | ----------- |
| `woption`     | The `woption` requests acknowledgment that the write operation has propagated to a specified number of mongod instances or to mongod instances with specified tags.  Mongo's `wOption` can be either an `Integer` or `String`, and this plugin implementation supports both with `woption`. <br/><br/>If `woption` is an `Integer`, then the write concern requests acknowledgment that the write operation has propagated to the specified number of mongod instances. Note: The `woption` cannot be set to zero. <br/><br/>If `woption` is a `String`, then the value can be either `"majority"` or a `tag set name`. If the value is `"majority"` then the write concern requests acknowledgment that write operations have propagated to the majority of voting nodes. If the value is a `tag set name`, the write concern requests acknowledgment that the write operations have propagated to a replica set member with the specified tag. The default value is an `Integer` value of `1`. |
| `wtimeout`    | This option specifies a time limit, in `milliseconds`, for the write concern. If you do not specify the `wtimeout` option, and the level of write concern is unachievable, the write operation will **block** indefinitely. Specifying a `wtimeout` value of `0` is equivalent to a write concern without the `wTimeout` option. The default value is `10000` (10 seconds). |

## Journal Configuration

### Activation

To activate the journal feature of the plugin, add the following line to your Akka `application.conf`. This will run the journal with its default settings.

```scala
akka.persistence.journal.plugin = "casbah-journal"
```

### Connection

The default `mongo-url` is a `string` with a value of:

```scala
casbah-journal.mongo-url = "mongodb://localhost:27017/store.messages"
```

<br/>This value can be changed in the `application.conf` with the following key:

```scala
casbah-journal.mongo-url

# Example
casbah-journal.mongo-url = "mongodb://localhost:27017/employee.events"
```

<br/>See the [Mongo Connection String URI](#mongo-connection-string-uri) section of this document for more information.

### Write Concern

The default `woption` is an `Integer` with a value of:

```scala
casbah-journal.woption = 1
```

<br/>This value can be changed in the `application.conf` with the following key:

```scala
casbah-journal.woption

# Example
casbah-journal.woption = "majority"
```

### Write Concern Timeout

The default `wtimeout` is an `Long` in milliseconds with a value of:

```scala
casbah-journal.wtimeout = 10000
```

<br/>This value can be changed in the `application.conf` with the following key:

```scala
casbah-journal.wtimeout

# Example
casbah-journal.wtimeout = 5000
```

<br/>See the [Mongo Write Concern](#mongo-write-concern) section of this document for more information.

## Snapshot Configuration

### Activation

To activate the snapshot feature of the plugin, add the following line to your Akka `application.conf`. This will run the snapshot-store with its default settings.

```scala
akka.persistence.snapshot-store.plugin = "casbah-snapshot"
```

### Connection

The default `mongo-url` is a `string` with a value of:

```scala
casbah-snapshot.mongo-url = "mongodb://localhost:27017/store.snapshots"
```

<br/>This value can be changed in the `application.conf` with the following key:

```scala
casbah-snapshot.mongo-url

# Example
casbah-snapshot.mongo-url = "mongodb://localhost:27017/employee.snapshots"
```

<br/>See the [Mongo Connection String URI](#mongo-connection-string-uri) section of this document for more information.

### Write Concern

The default `woption` is an `Integer` with a value of:

```scala
casbah-snapshot.woption = 1
```

<br/>This value can be changed in the `application.conf` with the following key:

```scala
casbah-snapshot.woption

# Example
casbah-snapshot.woption = "majority"
```

### Write Concern Timeout

The default `wtimeout` is an `Long` in milliseconds with a value of:

```scala
casbah-snapshot.wtimeout = 10000
```

<br/>This value can be changed in the `application.conf` with the following key:

```scala
casbah-snapshot.wtimeout

# Example
casbah-snapshot.wtimeout = 5000
```

<br/>See the [Mongo Write Concern](#mongo-write-concern) section of this document for more information.

### Snapshot Load Attempts

The snapshot feature of the plugin allows for the selection of the youngest of `{n}` snapshots that match an upper bound specified by configuration. This helps where a snapshot may not have persisted correctly because of a JVM crash. As a result an attempt to load the snapshot may fail but an older may succeed.

The default `load-attempts` is an `Integer` with a value of:

```scala
casbah-snapshot.load-attempts = 3
```

<br/>This value can be changed in the `application.conf` with the following key:

```scala
casbah-snapshot.load-attempts

# Example
casbah-snapshot.load-attempts = 5
```

## Status

* All operations required by the Akka Persistence [journal plugin API](http://doc.akka.io/docs/akka/current/scala/persistence.html#Journal_plugin_API) are supported.
* All operations required by the Akka Persistence [Snapshot store plugin API](http://doc.akka.io/docs/akka/current/scala/persistence.html#Snapshot_store_plugin_API) are supported.
* Tested against [Plugin TCK](http://doc.akka.io/docs/akka/current/scala/persistence.html#Plugin_TCK).
* Plugin uses [Asynchronous Casbah Driver](http://mongodb.github.io/casbah/3.1/)
* Message writes are batched to optimize throughput.

## Performance

Minimal performance testing is included against a **native** instance. In general the journal will persist around 8,000 to 10,000 messages per second.

## Sample Applications

The [sample applications](https://github.com/ironfish/akka-persistence-mongo-samples) are now located in their own repository.

## Change Log

### 1.0.0-SNAPSHOT

* Upgrade `Akka` 2.4.1.
* Upgrade `Casbah` to `Async` driver 3.1.
* Supports latest [Plugin TCK](http://doc.akka.io/docs/akka/current/scala/persistence.html#Plugin_TCK).

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
