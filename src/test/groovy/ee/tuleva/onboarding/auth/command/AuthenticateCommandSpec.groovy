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
    def cmd = new MobileIdAuthenticateCommand(phoneNumber, personalCode)

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
  }

  @Unroll
  def "invalid phone numbers"() {
    given:
    def cmd = new MobileIdAuthenticateCommand(phoneNumber, personalCode)

    when:
    def violations = validator.validate(cmd)

    then:
    violations.size() >= 1
    def violation = violations.iterator().next()
    violation.propertyPath.toString() == propertyName

    where:
    personalCode  | phoneNumber     | propertyName
    "38501010002" | "+++3725555555" | "phoneNumber"
    "38501010002" | "5555+55555"    | "phoneNumber"
    "38501010002" | "55555555+"     | "phoneNumber"
    "38501010002" | "555"           | "phoneNumber"
    "38501010002" | "+"             | "phoneNumber"
  }

  def cleanup() {
    validatorFactory.close()
  }

}
