# Mongo Journal for Akka Persistence Sample Command Sourcing Application

Home for Reactive application using the following stack:

- Akka-Persistence Mongo
- Embedded Mongo for testing
- Salat for case class serialization atop the mongo casbah driver
- Scalaz for non-breaking error handling
- ScalaStm for runtime domain state

## Description

This is a sample command sourcing application that shows some of what was have implemented in production using the Eventsourced library orginally written by
Martin Krasser, who turned us on to some of the concepts contained in this sample such as non-breaking error handling.  This example shows some of the ins
and outs of an employee domain and a read side (see CQRS) construct for the separate concern of HR (human resources) tracking the overall employment length
of employees.

Command sourcing is what we have used successfully in our production systems and effectively means we journal all of the commands but the events are only 
created/emitted upon successful validations of the commands.  Since this is command sourcing we don't use akka persistence views to represent our read store
because our read store operates upon the events, which of course are not journaled.  As a result the read side construct is more of a home grown type of thing
and uses basic akka functionality.

## Status

- Initial commit with base functionality.

## Author / Maintainer

- [Sean Walsh (@SeanWalshEsq)](https://github.com/sean-walsh/)

## Contributors

- [Duncan DeVore (@ironfish)](https://github.com/ddevore/)
- [Al Iacovella](https://github.com/aiacovella/)
