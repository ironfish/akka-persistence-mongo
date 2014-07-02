# Mongo Journal for Akka Persistence

[![Build Status](https://travis-ci.org/ddevore/akka-persistence-mongo.png?branch=master)](https://travis-ci.org/ddevore/akka-persistence-mongo)

A replicated [Akka Persistence](http://doc.akka.io/docs/akka/2.3.0/scala/persistence.html) journal backed by [MongoDB Casbah](http://mongodb.github.io/casbah/).

## Prerequisites

| Technology | Version                          |
| :--------: | -------------------------------- |
| Scala      | 2.10.4, 2.11.0 - Cross Compiled  |
| Akka       | 2.3.2 or higher                  |
| Mongo      | 2.4.8 or higher                  |

## Installation

The mongo journal driver is now available on the Maven Central Snapshot Repo.

### SBT

    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

    libraryDependencies ++= Seq(
      "com.github.ddevore" %% "akka-persistence-mongo-casbah"  % "0.7.3-SNAPSHOT" % "compile")

### Maven

#### Scala 2.10.4

    <dependency>
        <groupId>com.github.ddevore</groupId>
        <artifactId>akka-persistence-mongo-casbah_2.10</artifactId>
        <version>0.7.3-SNAPSHOT</version>
    </dependency>

#### Scala 2.11.0

    <dependency>
        <groupId>com.github.ddevore</groupId>
        <artifactId>akka-persistence-mongo-casbah_2.11</artifactId>
        <version>0.7.3-SNAPSHOT</version>
    </dependency>

### Build Locally

Build and install the journal plugin to your local Ivy cache with `sbt publishLocal` (requires sbt 0.13.2). It can then be included as dependency:

    libraryDependencies += "com.github.ddevore" %% "akka-persistence-mongo-casbah" % "0.7.3-SNAPSHOT"

## Journal Configuration

To activate the Mongo journal plugin, add the following line to your Akka `application.conf`:

    akka.persistence.journal.plugin = "casbah-journal"

This will run the journal with its default settings. The default settings can be changed with the following configuration keys:

### casbah-journal.mongo-journal-url

A comma-separated list of Mongo hosts. You can specify as many hosts as necessary, for example, connections to replica sets. The default value is `mongodb://localhost:27017/store.messages`. For more information on configuring the `mongo-url` see [Connection String Uri Format](http://docs.mongodb.org/manual/reference/connection-string/).

### casbah-journal.mongo-journal-write-concern

A journal must support the following akka-persistence property:

> When a processor's receive method is called with a Persistent message it can safely assume that this message has been successfully written to the journal.

As a result only the following write concerns are supported:

- `acknowledged` [Safe] - Exceptions are raised for network issues and server errors; waits on a server for the write operation.
- `journaled` [JournalSafe] - Exceptions are raised for network issues, and server errors; the write operation waits for the server to group commit to the journal file on disk.
- `replicas-acknowledged` [ReplicasSafe] - Exceptions are raised for network issues and server errors; waits for at least 2 servers for the write operation.

The default write concern is `journaled` [JournalSafe]. To better understand MongoDB `WriteConcern` see [Write Concern](http://docs.mongodb.org/manual/core/write-concern/).

### casbah.journal.mongo-journal-write-concern-timeout

This is an `Int` value that sets the timeout for the journal write concern. The default is 10000 millis [10 Seconds].

## Snapshot Configuration

To activate the Mongo snapshot plugin, add the following line to your Akka `application.conf`:

    akka.persistence.snapshot-store.plugin = "casbah-snapshot-store"

This will run the snapshot-store with its default settings. The default settings can be changed with the following configuration keys:

### casbah-snapshot-store.mongo-snapshot-url

A comma-separated list of Mongo hosts. You can specify as many hosts as necessary, for example, connections to replica sets. The default value is `mongodb://localhost:27017/store.snapshots`. For more information on configuring the `mongo-url` see [Connection String Uri Format](http://docs.mongodb.org/manual/reference/connection-string/).

### casbah-snapshot.mongo-snapshot-write-concern

A snapshot-store must support the following akka-persistence property:

> When a processor's receive method is called to persist a snapshot it can safely assume that snapshot has been successfully written.

As a result only the following write concerns are supported:

- `acknowledged` [Safe] - Exceptions are raised for network issues and server errors; waits on a server for the write operation.
- `journaled` [JournalSafe] - Exceptions are raised for network issues, and server errors; the write operation waits for the server to group commit to the journal file on disk.
- `replicas-acknowledged` [ReplicasSafe] - Exceptions are raised for network issues and server errors; waits for at least 2 servers for the write operation.

The default write concern is `journaled` [JournalSafe]. To better understand MongoDB `WriteConcern` see [Write Concern](http://docs.mongodb.org/manual/core/write-concern/).

### casbah.snapshot.mongo-snapshot-write-concern-timeout

This is an `Int` value that sets the timeout for the snapshot-store write concern. The default is 10000 millis [10 Seconds].

### casbah.snapshot.mongo-snapshot-load-attempts

Allows for the selection of the youngest of `{n}` snapshots that match the upper bound. This helps where a snapshot may not have persisted correctly because of a JVM crash. As a result an attempt to load the snapshot may fail but an older may succeed. This is an `Int` value that defaults to 3.

## Status

- All operations required by the Akka Persistence [journal plugin API](http://doc.akka.io/docs/akka/2.3.0/scala/persistence.html#journal-plugin-api) are supported.
- Message writes are batched to optimize throughput.
- When using channels, confirmation writes are batched to optimize throughput.
- Deletes (marked & permanent) are batched to optimize throughput.
- Sharding is not yet supported.
- Akka-Persistence is still considered **experimental** and as such the underlying api may change based on changes to Akka Persistence or user feedback.

## Performance

Minimal performance testing is included against a **native** instance. In general the journal will persist around 7000 to 8000 messages per second.

## Example
There is an [example application](https://github.com/ddevore/akka-persistence-mongo/tree/master/akka-persistence-mongo-command-sourcing-example-app) that implements Akka-Persistence [command sourcing](http://doc.akka.io/docs/akka/2.3.0/scala/persistence.html#Processors). In this example, the journal acts as a write-ahead-log for whatever persisted messages it recieves. 

There is also an [example application](https://github.com/ddevore/akka-persistence-mongo/tree/master/akka-persistence-mongo-event-sourcing-example-app) that implements Akka-Persistence [event sourcing](http://doc.akka.io/docs/akka/2.3.0/scala/persistence.html#Event_sourcing).


## Author / Maintainer

- [Duncan DeVore (@ironfish)](https://github.com/ddevore/)

## Contributors

- [Sean Walsh (@SeanWalshEsq)](https://github.com/sean-walsh/)
- [Al Iacovella](https://github.com/aiacovella/)
