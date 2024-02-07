package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.fund.ApiFundResponse
import spock.lang.Specification

import static ee.tuleva.onboarding.fund.FundFixture.lhv3rdPillarFund
import static ee.tuleva.onboarding.fund.FundFixture.tuleva3rdPillarFund

class ExchangeSpec extends Specification {
  def "getValue works with zeros with different precision"() {
    given:
    def exchange = new Exchange(
        new ApiFundResponse(lhv3rdPillarFund(), Locale.ENGLISH),
        new ApiFundResponse(tuleva3rdPillarFund(), Locale.ENGLISH),
        null,
        0.0
    )
    when:
    def result = exchange.getValue(totalValue, totalUnits)
    then:
    result == expectedResult
    where:
    totalValue      | totalUnits      || expectedResult
    BigDecimal.ZERO | BigDecimal.ZERO || BigDecimal.ZERO
    0.0             | 0.0             || BigDecimal.ZERO
    0.0000          | 0.0000          || BigDecimal.ZERO
  }
}
