package ee.tuleva.onboarding.notification.payment.validator

import ee.tuleva.onboarding.notification.payment.IncomingPayment
import spock.lang.Specification
import spock.lang.Unroll

class ValidMacCodeValidatorSpec extends Specification {

  def validator = new ValidMacCodeValidator()

@Unroll
  def "validates with secret: #secret"() {
    given:
    def incomingPayment = IncomingPayment.builder()
        .json('{"shop":"id","amount":"100"}')
        .mac("B3D4A05423F11E8B67B98FC9C9F603220EFAEC2AE74329513B456461577683EA4883DA500625F532A247D900F14E10D6597AEE473AD0E5F43F1008DECAA7C80D")
        .build()
    validator.secret = secret

    when:
    def result = validator.isValid(incomingPayment, null)

    then:
    result == isValid

    where:
    secret        | isValid
    "validSecret" | true
    "wrongSecret" | false
  }

}
