package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.applicationtype.ApplicationType
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

class PaymentRateApplicationDetailsSpec extends Specification {

  def "constructor rejects a type that is not a payment rate change"() {
    when:
    new PaymentRateApplicationDetails(
        BigDecimal.valueOf(4), Instant.now(), LocalDate.now(), ApplicationType.TRANSFER)

    then:
    thrown(IllegalArgumentException)
  }

  def "getPillar is always the 2nd pillar"() {
    expect:
    new PaymentRateApplicationDetails(
        BigDecimal.valueOf(4), Instant.now(), LocalDate.now(), ApplicationType.PAYMENT_RATE)
        .getPillar() == 2
  }
}
