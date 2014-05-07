package org

import scalaz._
import Scalaz._

package object esexample {
  type DomainValidation[+α] = Validation[NonEmptyList[String], α]

  /**
   * Trait for validation errors
   */
  trait ValidationKey {
    def failNel = this.toString.failNel
    def nel = NonEmptyList(this.toString)
    def failure = this.toString.fail
  }

  object CommonValidations {
    /**
     * Validates that a string is not null and non empty
     *
     * @param s String to be validated
     * @param err ValidationKey
     * @return Validation
     */
    def checkString(s: String, err: ValidationKey): Validation[String, String] =
      if (s == null || s.isEmpty) err.failure else s.success

    /**
     * Validates that a date is a non zero Long value.
     *
     * @param d Long to be validated
     * @param err ValidationKey
     * @return Validation
     */
    def checkDate(d: Long, err: ValidationKey): Validation[String, Long] =
      if (d <= 0) err.failure else d.success
  }

  sealed case class Address (
    street: String,
    city: String,
    stateOrProvince: String,
    country: String,
    postalCode: String
  )

  object Address {
    import CommonValidations._

    case object StreetRequired extends ValidationKey
    case object CityRequired extends ValidationKey
    case object StateOrProvinceRequired extends ValidationKey
    case object CountryRequired extends ValidationKey
    case object PostalCodeRequired extends ValidationKey

    def validate(street: String, city: String, stateOrProvince: String, country: String, postalCode: String):
        ValidationNel[String, Address] =
      (checkString(street, StreetRequired).toValidationNel |@|
        checkString(city, CityRequired).toValidationNel |@|
        checkString(stateOrProvince, StateOrProvinceRequired).toValidationNel |@|
        checkString(country, CountryRequired).toValidationNel |@|
        checkString(postalCode, PostalCodeRequired).toValidationNel) {
        Address(_, _, _, _, _)
      }
  }
}
