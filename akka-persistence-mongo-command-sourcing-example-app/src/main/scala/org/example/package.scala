package org

import scalaz._
import Scalaz._

package object example {
  type DomainValidation[+α] = Validation[NonEmptyList[String], α]

  object CommonValidations {
    def checkString(s: String): Validation[String, String] =
      if (s == null || s.isEmpty) "InvalidString".failure else s.success

    def checkDate(d: Long): Validation[String, Long] =
      if (d > 0) d.success else "InvalidDate".failure
  }

  sealed case class Address private (
    line1: String,
    line2: String,
    city: String,
    stateOrProvince: String,
    country: String,
    postalCode: String
  )

  object Address {
    import CommonValidations._
    def create(line1: String, line2: String, city: String, stateOrProvince: String, country: String, postalCode: String): ValidationNel[String, Address] =
      (checkString(line1).toValidationNel |@|
        checkString(line2).toValidationNel |@|
        checkString(city).toValidationNel |@|
        checkString(stateOrProvince).toValidationNel |@|
        checkString(country).toValidationNel |@|
        checkString(postalCode).toValidationNel) {
        Address(_, _, _, _, _, _)
      }
  }
}
