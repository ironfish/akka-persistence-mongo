# Sample CQRS / Event Sourcing Application

Home for Reactive application using the following stack:

- Akka-Persistence
  - [Mongo Journal](https://github.com/ddevore/akka-persistence-mongo/) for journal persistence.
  - [EventsourcedProcessor](http://doc.akka.io/docs/akka/current/scala/persistence.html#Event_sourcing) for processing commands and journaling events.
  - [Views](http://doc.akka.io/docs/akka/current/scala/persistence.html#Views) for projecting CQRS query-side data.
- [Scalaz](https://github.com/scalaz/scalaz) for non-breaking error handling.
- [Salat](https://github.com/novus/salat) for case class serialization atop the mongo casbah driver on query-side.
- [Embedded Mongo](https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo) for test store.
- [ScalaTest](http://www.scalatest.org/) for testing.

## Description

This sample project demonstrates how to implement an [Akka Persistence](http://doc.akka.io/docs/akka/2.3.2/scala/persistence.html)
event sourcing application. A fictitious domain model that represents an employee management lifecyle is used in which domain commands are issued, and if valid generate one or more domain events which are then journaled (persisted).

### Event Sourcing

Event sourcing is an architectural pattern where a domain entities current state is not serialized or persisted, rather a sequence of domain events that represent changes in state to the entity are recorded. This is a very powerful (and old) construct which allows for the recovery of current state of a domain entity at any point in time from origin to now. Typically an event sourcing system will maintain a current state model in-memory for fast access. Such is the case with this example.

#### Domain Command

Domain commands are behavioral constructs that request changes to the state of a domain entity. They are imperative by nature and as such represent a request that can be rejected. Domain commands should be constructed in `Verb-Noun` format, so for example `HireEmployee` would be a domain command. A domain command can also represent requests to change state in aggregate (similiar to batching) from which, if valid, can generate many domain events.

#### Domain Event

Domain events represent the true state changes to a domain entity. They are a historical recording of what "has happend" and as such cannot be rejected. In an event sourcing system domain events are the result of processing valid domain commands an are the behavioral constructs that are persisted. A domain event follows `Noun-PastTenseVerb` format, for example `EmployeeHired`.

### CQRS

Command Query Responsibility Segregation is an architectural pattern created by Greg Young, based on Bertrand Meyer’s Command and Query Separation principle. Wikipedia defines this principle as:

> It states that every method should either be a command that performs an action, or a query that returns data to the caller, but not both. In other words, asking a question should not change the answer. More formally, methods should return a value only if they are referentially transparent and hence possess no side effects. (Wikipedia)

CQRS is similar to CQS in that it uses the same definition for Commands and Queries, but fundamentally believes that Commands and Queries should distinct objects in the domain. One of the key advantages to CQRS is that because the Commands (writes) are separate from the Queries (reads) it allows for distinct optimization of each of these concerns.

### DDD Implementation

// TODO - explain 1-n model as opposed to 1-1.

### Example Workflow

// TODO - explain CQRS implementation where command side aggregate in-memory model can be queried.

## Status

- ~~Initial commit with base functionality~~
- ~~Add State changes~~
    - ~~Deactive~~
    - ~~Activate~~
    - ~~Terminate~~
    - ~~Rehire~~
- ~~Add Run Payroll~~
- ~~Implement base tests~~
- ~~Implement CQRS query side w/ views~~
- ~~Implement CQRS tests~~
- Implement Channel
- Implement Channel tests

## Disclaimer

// TODO

## Author / Maintainer

- [Duncan DeVore (@ironfish)](https://github.com/ddevore/)

## Contributors

- [Sean Walsh (@SeanWalshEsq)](https://github.com/sean-walsh/)
- [Al Iacovella](https://github.com/aiacovella/)
