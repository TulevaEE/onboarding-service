package ee.tuleva.onboarding.user.personalcode

import spock.lang.Specification
import spock.lang.Unroll

class PersonalCodeValidatorSpec extends Specification {

  PersonalCodeValidator validator = new PersonalCodeValidator()

  @Unroll
  def 'validates personal code: "#personalCode"'() {
    when:
    def response = validator.isValid(personalCode, null)
    then:
    response == isValid
    where:
    personalCode  | isValid
    null          | false
    ""            | false
    " "           | false
    "3"           | false
    "aaaaaaaaaaa" | false
    "0000000-100" | false
    "00000000000" | false
    "17810010006" | false // invalid century
    "27810010007" | false // invalid century
    "37802310009" | false // invalid date
    "37605030299" | true
  }

  @Unroll
  def 'checksum for "#personalCode"'() {
    when:
    def result = validator.calculateChecksum(personalCode)
    then:
    result == checksum
    where:
    personalCode  | checksum
    "37605030299" | 9
    "57802286060" | 0
  }
}
