package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.applicationtype.ApplicationType
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

class FundPensionOpeningApplicationDetailsSpec extends Specification {

  def "constructor rejects a type that is not fund pension opening"() {
    when:
    new FundPensionOpeningApplicationDetails(
        "IBAN", Instant.now(), LocalDate.now(), ApplicationType.TRANSFER, null)

    then:
    thrown(IllegalArgumentException)
  }

  def "getPillar maps the application type to its pillar"() {
    expect:
    new FundPensionOpeningApplicationDetails(
        "IBAN", Instant.now(), LocalDate.now(), ApplicationType.FUND_PENSION_OPENING, null)
        .getPillar() == 2
    new FundPensionOpeningApplicationDetails(
        "IBAN", Instant.now(), LocalDate.now(), ApplicationType.FUND_PENSION_OPENING_THIRD_PILLAR, null)
        .getPillar() == 3
  }
}
