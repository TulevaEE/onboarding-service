package ee.tuleva.onboarding.auth.command

import spock.lang.Specification
import spock.lang.Unroll

import jakarta.validation.Validation

class AuthenticateCommandSpec extends Specification {

  def validatorFactory = Validation.buildDefaultValidatorFactory()
  def validator = validatorFactory.getValidator()

  @Unroll
  def "valid phone numbers"() {
    given:
    def cmd = new MobileIdAuthenticateCommand(phoneNumber, personalCode, false)

    when:
    def violations = validator.validate(cmd)

    then:
    violations.isEmpty()

    where:
    personalCode  | phoneNumber
    "38501010002" | "5555555"
    "38501010002" | "55555555"
    "38501010002" | "+3725555555"
    "38501010002" | "+37255555555"
    "38501010002" | "+372 5555 5555"
    "38501010002" | "5555-5555"
    "38501010002" | "(+372) 5555 5555"
    "38501010002" | null
    "38501010002" | ""
    "38501010002" | "   "
  }

  @Unroll
  def "invalid phone numbers"() {
    given:
    def cmd = new MobileIdAuthenticateCommand(phoneNumber, personalCode, false)

    when:
    def violations = validator.validate(cmd)

    then:
    violations.size() >= 1
    def violation = violations.iterator().next()
    violation.propertyPath.toString() == propertyName

    where:
    personalCode  | phoneNumber | propertyName
    "38501010001" | "55555555"  | "personalCode"
    "38501010001" | null        | "personalCode"
  }

  def cleanup() {
    validatorFactory.close()
  }

}
