package org.esexample

/**
 * common messages used in testing
 */
case class Msg(deliveryId: Long, payload: Any)

case class Confirm(deliveryId: Long)


