package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.fund.ApiFundResponse
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.fund.FundFixture.lhv2ndPillarFund
import static ee.tuleva.onboarding.fund.FundFixture.lhv3rdPillarFund
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarStockFund
import static ee.tuleva.onboarding.fund.FundFixture.tuleva3rdPillarFund

class ExchangeSpec extends Specification {

  def tulevaFund2nd = new ApiFundResponse(tuleva2ndPillarStockFund(), Locale.ENGLISH)
  def lhvFund2nd = new ApiFundResponse(lhv2ndPillarFund(), Locale.ENGLISH)
  def lhvFund3rd = new ApiFundResponse(lhv3rdPillarFund(), Locale.ENGLISH)
  def tulevaFund3rd = new ApiFundResponse(tuleva3rdPillarFund(), Locale.ENGLISH)
  def pikAccount = "EE471000001020145685"

  def "getValue works with zeros with different precision"() {
    given:
    def exchange = new Exchange(lhvFund3rd, tulevaFund3rd, null, 0.0)
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

  def "getValue for 2nd pillar multiplies the amount fraction by total value, ignoring units"() {
    given:
    def exchange = new Exchange(tulevaFund2nd, lhvFund2nd, null, 0.5)
    expect:
    exchange.getValue(200.0, 1.0) == 100.00
    exchange.getValue(200.0, 999999.0) == 100.00
  }

  def "getValue for 3rd pillar divides amount times total value by total units, rounded to 2 decimals"() {
    given:
    def exchange = new Exchange(lhvFund3rd, tulevaFund3rd, null, 3.0)
    expect:
    exchange.getValue(90.0, 9.0) == 30.00
  }

  @Unroll
  def "2nd pillar exchange accepts an amount fraction of #amount"() {
    when:
    new Exchange(tulevaFund2nd, lhvFund2nd, null, amount)
    then:
    noExceptionThrown()
    where:
    amount << [0.0001, 1.0]
  }

  @Unroll
  def "2nd pillar exchange rejects an amount fraction of #amount"() {
    when:
    new Exchange(tulevaFund2nd, lhvFund2nd, null, amount)
    then:
    thrown(IllegalArgumentException)
    where:
    amount << [0.0, 1.0001, null]
  }

  def "3rd pillar exchange amount is not restricted to the 2nd pillar 0-1 range"() {
    when:
    new Exchange(lhvFund3rd, tulevaFund3rd, null, 5.0)
    then:
    noExceptionThrown()
  }

  def "isFullAmount for 2nd pillar is true only when the whole book value (amount 1) is exchanged"() {
    expect:
    new Exchange(tulevaFund2nd, lhvFund2nd, null, 1.0).isFullAmount()
    !new Exchange(tulevaFund2nd, lhvFund2nd, null, 0.5).isFullAmount()
  }

  def "isFullAmount() rejects a 3rd pillar exchange"() {
    given:
    def exchange = new Exchange(lhvFund3rd, tulevaFund3rd, null, 3.0)
    when:
    exchange.isFullAmount()
    then:
    thrown(IllegalStateException)
  }

  def "isFullAmount(units) for 3rd pillar compares the exchanged amount to the fund balance"() {
    given:
    def exchange = new Exchange(lhvFund3rd, tulevaFund3rd, null, 10.0)
    expect:
    exchange.isFullAmount(10.0)
    !exchange.isFullAmount(10.01)
  }

  def "isFullAmount(units) rejects a 2nd pillar exchange"() {
    given:
    def exchange = new Exchange(tulevaFund2nd, lhvFund2nd, null, 1.0)
    when:
    exchange.isFullAmount(10.0)
    then:
    thrown(IllegalStateException)
  }

  def "isToPik is true only when exchanging into a PIK account instead of a fund"() {
    given:
    def toFund = new Exchange(tulevaFund2nd, lhvFund2nd, null, 1.0)
    def toPik = new Exchange(tulevaFund2nd, null, pikAccount, 1.0)
    expect:
    !toFund.isToPik()
    toPik.isToPik()
  }

  def "isFromOwnFund reflects whether the source fund is managed by Tuleva"() {
    expect:
    new Exchange(tulevaFund2nd, lhvFund2nd, null, 1.0).isFromOwnFund()
    !new Exchange(lhvFund2nd, tulevaFund2nd, null, 1.0).isFromOwnFund()
  }

  def "isToOwnFund reflects whether the target fund is managed by Tuleva, and is false for a PIK target"() {
    expect:
    new Exchange(lhvFund2nd, tulevaFund2nd, null, 1.0).isToOwnFund()
    !new Exchange(tulevaFund2nd, lhvFund2nd, null, 1.0).isToOwnFund()
    !new Exchange(tulevaFund2nd, null, pikAccount, 1.0).isToOwnFund()
  }

  def "getSourceIsin and getTargetIsin expose the underlying fund identifiers"() {
    given:
    def exchange = new Exchange(tulevaFund2nd, lhvFund2nd, null, 1.0)
    expect:
    exchange.getSourceIsin() == tulevaFund2nd.getIsin()
    exchange.getTargetIsin() == lhvFund2nd.getIsin()
  }

  def "getSourceFundFees and getTargetFundFees expose the underlying fund ongoing charges"() {
    given:
    def exchange = new Exchange(tulevaFund2nd, lhvFund2nd, null, 1.0)
    expect:
    exchange.getSourceFundFees() == tulevaFund2nd.getOngoingChargesFigure()
    exchange.getTargetFundFees() == lhvFund2nd.getOngoingChargesFigure()
  }
}
