# Sample CQRS / Event Sourcing Application

Home for a Reactive application using the following stack:

- Akka-Persistence
  - [Mongo Journal](https://github.com/ddevore/akka-persistence-mongo/) for journal persistence.
  - [EventsourcedProcessor](http://doc.akka.io/docs/akka/current/scala/persistence.html#Event_sourcing) for processing commands and journaling events.
  - [View](http://doc.akka.io/docs/akka/current/scala/persistence.html#Views) for projecting CQRS query-side data.
  - [Channels](http://doc.akka.io/docs/akka/2.3.2/scala/persistence.html#Channels) for destination delivery confirmation.
- [Scalaz](https://github.com/scalaz/scalaz) for non-breaking error handling.
- [Salat](https://github.com/novus/salat) for case class serialization atop the mongo casbah driver on the query-side.
- [Embedded Mongo](https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo) for test store.
- [ScalaTest](http://www.scalatest.org/) for testing.

## Description

This sample project demonstrates one way to implement an [Akka Persistence](http://doc.akka.io/docs/akka/2.3.2/scala/persistence.html)
event sourcing application. A fictitious domain model that represents an employee management lifecyle is used in which domain commands are issued, and if valid generate one or more domain events which are then journaled (persisted).

This application does not have a UI component and is not intended to be a complete HR system. Rather it's designed to demonstrate the use of the above technologies in a simple use case.

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

This example implements CQRS in the following way:

* The Command side is represented with two scala files, `Employee.scala` & `EmployeeProtocol.scala`.
* The Query side is represented with two scala files, `Benefits.scala` & `BenefitsProtocol.scala`.

The command side will process domain commands and if valid generate domain events that are jounaled. These events are made _eventually consistent_ with the query side by way of an Akka-Persistence `View`.

### DDD Implementation

There has been a fair amount of discussion around how to design a distributed domain model using Akka. See the following links for information around a design pattern that seems to be materializing:

* [Creating one instance of an Akka actor per entity](https://groups.google.com/forum/#!topic/akka-user/BRh3YNjP0kY)
* [Cluster sharding for akka-persistence](https://groups.google.com/forum/#!msg/akka-dev/ohdT-Et4ZoY/6cB52mnpkAkJ)
* [Using Scala and Akka with Domain-Driven Design](https://vaughnvernon.co/?p=770)

The general approach identified in the links above is one of a single actor per aggregate, 1-1. While I think in the long run this is a better approach (especially in regards to clustering), I chose not to implement this design for the example as it's somewhat more complicated and still a design in progress.

The command side implements a 1-n, single actor per aggregate type. Aggregates are modeled as case classes with an associated `EventsourcedProcessor`. The primary aggregate is the `Employee` aggregate which can be in one of the three following states:

* `ActiveEmployee` - the employee has been hired and is currently active.
* `InactiveEmployee` - the employee has been temporarily deactivated (ie. leave of abscence)
* `TerminatedEmployee` - the employee has been terminated.

The read side implements a single aggregate the `BenefitDates` aggregate. This aggregate is persisted whenever certain events are made _eventually consistent_ from the command side. The events from the command side that trigger this behavior are:

* `EmployeeHired` - whenever an employee is hired.
* `EmployeeDeactivated` - whenever a employee id deactivated (ie. leave of abscence).
* `EmployeeActivated` - whenever a deactivated employee is activated.
* `EmployeeTerminated` - whenever an active employee is terminated.
* `EmployeeRehired` - whenever a terminated employee is rehired.

### Workflow

The command side has a single `EmployeeProcessor` of type `EventsourcedProcessor` which is responsible for managing the employee lifecycle. This entails the following responsibilities:

* Process commands, which if valid generate events.
* Journaling valid events.
* Updating the in-memory model for current state of a given aggregate. This update may result in the change of the `Employee` type.

On the query side the `BenefitsView` which is an Akka-Persistence `View` is tied to the `EmployeeProcessor`. This view receives certain events (see above) which are persisted. Once persisted a message is created and delivered to a fictitious destination, `BenefitsDestination` via an Akka-Persistence `Channel` for confirmation.

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
- ~~Implement Channel~~
- ~~Implement Channel tests~~
- ~~Implement get~~

## Author / Maintainer

- [Duncan DeVore (@ironfish)](https://github.com/ddevore/)

## Contributors

- [Sean Walsh (@SeanWalshEsq)](https://github.com/sean-walsh/)
- [Al Iacovella](https://github.com/aiacovella/)
