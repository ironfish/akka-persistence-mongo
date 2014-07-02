package org.example

import akka.persistence.SnapshotMetadata

/**
 * Common messages dispatched to the event stream for probing in tests
 */
sealed trait Confirmations
case class MsgConfirmed(deliveryId: Long) extends Confirmations
case class SnapshotConfirmed(snap: SnapshotMetadata) extends Confirmations