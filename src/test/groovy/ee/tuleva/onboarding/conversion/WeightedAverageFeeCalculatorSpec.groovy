package ee.tuleva.onboarding.conversion


import ee.tuleva.onboarding.account.FundBalance
import ee.tuleva.onboarding.fund.ApiFundResponse
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.mandate.application.Exchange
import org.springframework.context.i18n.LocaleContextHolder
import spock.lang.Specification

import static ee.tuleva.onboarding.fund.FundFixture.*

class WeightedAverageFeeCalculatorSpec extends Specification {

  WeightedAverageFeeCalculator weightedAverageFeeCalculator = new WeightedAverageFeeCalculator()

  def "Calculates the weighted average fee correctly"() {
    given:
    List<FundBalance> funds = fundData.collect { fundInfo ->
      Fund fund = Fund.builder()
          .ongoingChargesFigure(fundInfo.fundFee)
          .isin(fundInfo.isin)
          .build()

      FundBalance.builder()
          .fund(fund)
          .value(fundInfo.value)
          .unavailableValue(fundInfo.unavailableValue)
          .build()
    }
    def pendingExchanges = []

    expect:
    weightedAverageFeeCalculator.getWeightedAverageFee(funds, pendingExchanges) == expectedWeightedAverageFee

    where:
    fundData                                                                                               | expectedWeightedAverageFee
    []                                                                                                     | 0.0
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 0.0, fundFee: 0.01, unavailableValue: 0.0]
    ]                                                                                                      | 0.0
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 100, fundFee: 0.01, unavailableValue: 0.0]
    ]                                                                                                      | 0.01
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 100, fundFee: 0.02, unavailableValue: 0.0],
        [isin: lhv2ndPillarFund.isin, value: 200, fundFee: 0.03, unavailableValue: 0.0]
    ]                                                                                                      | 0.0267
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 100, fundFee: 0.02, unavailableValue: 100],
        [isin: lhv2ndPillarFund.isin, value: 200, fundFee: 0.03, unavailableValue: 0.0]
    ]                                                                                                      | 0.0250
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 0.0000001, fundFee: 0.000002, unavailableValue: 0.0],
        [isin: lhv2ndPillarFund.isin, value: 0.0000002, fundFee: 0.000003, unavailableValue: 0.0]
    ]                                                                                                      | 0.0
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 0.0, fundFee: 0.01, unavailableValue: 0.0],
        [isin: lhv2ndPillarFund.isin, value: 0.0, fundFee: 0.02, unavailableValue: 0.0]
    ]                                                                                                      | 0.0
  }

  def "Calculates the weighted average fee correctly with pending exchanges"() {
    given:
    List<FundBalance> funds = fundData.collect { fundInfo ->
      Fund fund = Fund.builder()
          .ongoingChargesFigure(fundInfo.fundFee)
          .isin(fundInfo.isin)
          .build()

      FundBalance.builder()
          .fund(fund)
          .value(fundInfo.value)
          .unavailableValue(fundInfo.unavailableValue)
          .build()
    }

    def locale = LocaleContextHolder.getLocale()


    def sourceFund = tuleva2ndPillarStockFund
    def targetFund = lhv2ndPillarFund.tap { ongoingChargesFigure = 0.01 }
    def pendingExchanges = [new Exchange(
        new ApiFundResponse(sourceFund, locale),
        new ApiFundResponse(targetFund, locale),
        null,
        1.0 // 100%
    )]

    expect:
    weightedAverageFeeCalculator.getWeightedAverageFee(funds, pendingExchanges) == expectedWeightedAverageFee

    where:
    fundData                                                                                       | expectedWeightedAverageFee
    []                                                                                             | 0.0
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 0.0, fundFee: 0.005, unavailableValue: 0.0]
    ]                                                                                              | 0.0
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 100.0, fundFee: 0.005, unavailableValue: 0.0]
    ]                                                                                              | 0.01
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 100.0, fundFee: 0.02, unavailableValue: 0.0],
        [isin: tuleva2ndPillarBondFund.isin, value: 200.0, fundFee: 0.03, unavailableValue: 0.0]
    ]                                                                                              | ((200 * 0.03 + 100 * 0.01) / 300).round(4)
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 100.0, fundFee: 0.02, unavailableValue: 100],
        [isin: tuleva2ndPillarBondFund.isin, value: 200.0, fundFee: 0.03, unavailableValue: 0.0]
    ]                                                                                              | ((200 * 0.03 + 200 * 0.01) / 400).round(4)
  }

  def "Calculates the weighted average fee correctly with pending pik exchanges"() {
    given:
    List<FundBalance> funds = fundData.collect { fundInfo ->
      Fund fund = Fund.builder()
          .ongoingChargesFigure(fundInfo.fundFee)
          .isin(fundInfo.isin)
          .build()

      FundBalance.builder()
          .fund(fund)
          .value(fundInfo.value)
          .unavailableValue(fundInfo.unavailableValue)
          .build()
    }

    def locale = LocaleContextHolder.getLocale()

    def exchange = new Exchange(
        new ApiFundResponse(tuleva2ndPillarStockFund, locale),
        null,
        "target PIK",
        1.0 // 100%
    )

    def pendingExchanges = [exchange]

    expect:
    weightedAverageFeeCalculator.getWeightedAverageFee(funds, pendingExchanges) == expectedWeightedAverageFee

    where:
    fundData                                                                                         | expectedWeightedAverageFee
    []                                                                                               | 0.0
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 0.0, fundFee: 0.02, unavailableValue: 0.0]
    ]                                                                                                | 0.0
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 100.0, fundFee: 0.01, unavailableValue: 0.0]
    ]                                                                                                | 0.0
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 100.0, fundFee: 0.02, unavailableValue: 0.0],
        [isin: lhv2ndPillarFund.isin, value: 200.0, fundFee: 0.03, unavailableValue: 0.0]
    ]                                                                                                | 0.03
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 100.0, fundFee: 0.02, unavailableValue: 100.0],
        [isin: lhv2ndPillarFund.isin, value: 200.0, fundFee: 0.03, unavailableValue: 0.0]
    ]                                                                                                | 0.03
  }
}
