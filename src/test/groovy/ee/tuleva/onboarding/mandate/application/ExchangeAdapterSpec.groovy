package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.fund.ApiFundResponse
import spock.lang.Specification

import static ee.tuleva.onboarding.fund.FundFixture.lhv2ndPillarFund
import static ee.tuleva.onboarding.fund.FundFixture.lhv3rdPillarFund
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarStockFund
import static ee.tuleva.onboarding.fund.FundFixture.tuleva3rdPillarFund

class ExchangeAdapterSpec extends Specification {

  def tulevaFund2nd = new ApiFundResponse(tuleva2ndPillarStockFund(), Locale.ENGLISH)
  def lhvFund2nd = new ApiFundResponse(lhv2ndPillarFund(), Locale.ENGLISH)
  def lhvFund3rd = new ApiFundResponse(lhv3rdPillarFund(), Locale.ENGLISH)
  def tulevaFund3rd = new ApiFundResponse(tuleva3rdPillarFund(), Locale.ENGLISH)
  def pikAccount = "EE471000001020145685"

  def "delegates every method to a full 2nd pillar exchange from the own fund, not to a PIK"() {
    given:
    def exchange = new Exchange(tulevaFund2nd, lhvFund2nd, null, 1.0)
    def adapter = new ExchangeAdapter(exchange)

    expect:
    adapter.getPillar() == exchange.getPillar()
    adapter.getPillar() == 2
    adapter.isFromOwnFund() == exchange.isFromOwnFund()
    adapter.isFromOwnFund()
    adapter.isToOwnFund() == exchange.isToOwnFund()
    !adapter.isToOwnFund()
    adapter.isFullAmount() == exchange.isFullAmount()
    adapter.isFullAmount()
    adapter.isToPik() == exchange.isToPik()
    !adapter.isToPik()
    adapter.getSourceIsin() == exchange.getSourceIsin()
    adapter.getTargetIsin() == exchange.getTargetIsin()
    adapter.getSourceFundFees() == exchange.getSourceFundFees()
    adapter.getTargetFundFees() == exchange.getTargetFundFees()
    adapter.getValue(200.0, 1.0) == exchange.getValue(200.0, 1.0)
    adapter.getValue(200.0, 1.0) == 200.00
  }

  def "delegates every method to a partial 2nd pillar exchange into a PIK, from a foreign fund"() {
    given:
    def exchange = new Exchange(lhvFund2nd, null, pikAccount, 0.5)
    def adapter = new ExchangeAdapter(exchange)

    expect:
    adapter.isFromOwnFund() == exchange.isFromOwnFund()
    !adapter.isFromOwnFund()
    adapter.isToOwnFund() == exchange.isToOwnFund()
    !adapter.isToOwnFund()
    adapter.isFullAmount() == exchange.isFullAmount()
    !adapter.isFullAmount()
    adapter.isToPik() == exchange.isToPik()
    adapter.isToPik()
  }

  def "delegates every method to a 3rd pillar exchange into the own fund"() {
    given:
    def exchange = new Exchange(lhvFund3rd, tulevaFund3rd, null, 10.0)
    def adapter = new ExchangeAdapter(exchange)

    expect:
    adapter.getPillar() == exchange.getPillar()
    adapter.getPillar() == 3
    adapter.isFromOwnFund() == exchange.isFromOwnFund()
    !adapter.isFromOwnFund()
    adapter.isToOwnFund() == exchange.isToOwnFund()
    adapter.isToOwnFund()
    adapter.isFullAmount(10.0) == exchange.isFullAmount(10.0)
    adapter.isFullAmount(10.0)
    adapter.isFullAmount(1.0) == exchange.isFullAmount(1.0)
    !adapter.isFullAmount(1.0)
    adapter.getValue(90.0, 9.0) == exchange.getValue(90.0, 9.0)
    adapter.getValue(90.0, 9.0) == 100.00
  }
}
