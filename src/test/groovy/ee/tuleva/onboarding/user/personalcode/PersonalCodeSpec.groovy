package ee.tuleva.onboarding.user.personalcode

import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.user.personalcode.Gender.FEMALE
import static ee.tuleva.onboarding.user.personalcode.Gender.MALE

class PersonalCodeSpec extends Specification {

  def "can get date of birth with different centuries: #personalCode"() {
    expect:
    PersonalCode.getDateOfBirth(personalCode) == LocalDate.parse(dateOfBirth)

    where:
    personalCode  | dateOfBirth
    "38501020000" | "1985-01-02"
    "48501020000" | "1985-01-02"
    "50301020000" | "2003-01-02"
    "60301020000" | "2003-01-02"
  }

  def "can get gender: #personalCode"() {
    expect:
    PersonalCode.getGender(personalCode) == gender

    where:
    personalCode  | gender
    "18501020000" | MALE
    "28501020000" | FEMALE
    "38501020000" | MALE
    "48501020000" | FEMALE
    "50301020000" | MALE
    "60301020000" | FEMALE
  }


}
