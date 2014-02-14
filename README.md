# Mongo Journal for Akka Persistence

[![Build Status](https://travis-ci.org/ddevore/akka-persistence-mongo.png?branch=master)](https://travis-ci.org/ddevore/akka-persistence-mongo)

A replicated [Akka Persistence](http://doc.akka.io/docs/akka/2.3.0-RC1/scala/persistence.html) journal backed by [MongoDB Casbah](http://mongodb.github.io/casbah/).

## Prerequisites

<table border="0">
  <tr>
    <td>Akka version: </td>
    <td>2.3.0-RC1 or higher</td>
  </tr>
  <tr>
    <td>Mongo version: </td>
    <td>2.4.8 or higher</td>
  </tr>
</table>

## Installation

Build and install the journal plugin to your local Ivy cache with `sbt publishLocal` (requires sbt 0.13). It can then be included as dependency:

    libraryDependencies += "com.github.ddevore" %% "akka-persistence-mongo-casbah" % "0.3-SNAPSHOT"

## Configuration

To activate the Mongo journal plugin, add the following line to your Akka `application.conf`:

    akka.persistence.journal.plugin = "casbah-journal"

This will run the journal with its default settings. The default settings can be changed with the following configuration keys:

### casbah-journal.mongo-url

A comma-separated list of Mongo hosts. You can specify as many hosts as necessary, for example, connections to replica sets. Default value is `mongodb://localhost:27017/store.messages`. For more information on configuring the `mongo-url` see [Connection String Uri Format](http://docs.mongodb.org/manual/reference/connection-string/).

### casbah-journal.mongo-journal-write-concern

A journal must support the following akka-persistence property:

> When a processor's receive method is called with a Persistent message it can safely assume that this message has been successfully written to the journal.

As a result only the following write concerns are supported:

- `acknowledged` [Safe] - Exceptions are raised for network issues and server errors; waits on a server for the write operation.
- `journaled` [JournalSafe] - Exceptions are raised for network issues, and server errors; the write operation waits for the server to group commit to the journal file on disk.
- `replicas-acknowledged` [ReplicasSafe] - Exceptions are raised for network issues and server errors; waits for at least 2 servers for the write operation.

The default write concern is `acknowledged` [Safe]. To better understand MongoDB `WriteConcern` see [Write Concern](http://docs.mongodb.org/manual/core/write-concern/).

## Status

- All operations required by the Akka Persistence [journal plugin API](http://doc.akka.io/docs/akka/2.3.0-RC1/scala/persistence.html#journal-plugin-api) are supported.
- Message writes are batched to optimize throughput.
- When using channels, confirmation writes are batched to optimize throughput.
- Deletes (marked & permanent) are batched to optimize throughput.
- Sharding is not yet supported.
- This should be considered **experimental** as Akka-Persistence is still changing and the underlying storage structure may change.

## Performance

Minimal performance testing is included against a **native** instance. In general the journal will persist around 7000 to 8000 messages per second.

