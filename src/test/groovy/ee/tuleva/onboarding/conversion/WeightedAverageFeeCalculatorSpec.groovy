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
    fundData                                                                                                 | expectedWeightedAverageFee
    []                                                                                                       | 0.0
    [
        [isin: tuleva2ndPillarStockFund().isin, value: 0.0, fundFee: 0.01, unavailableValue: 0.0]
    ]                                                                                                        | 0.0
    [
        [isin: tuleva2ndPillarStockFund().isin, value: 100, fundFee: 0.01, unavailableValue: 0.0]
    ]                                                                                                        | 0.01
    [
        [isin: tuleva2ndPillarStockFund().isin, value: 100, fundFee: 0.02, unavailableValue: 0.0],
        [isin: lhv2ndPillarFund().isin, value: 200, fundFee: 0.03, unavailableValue: 0.0]
    ]                                                                                                        | 0.0267
    [
        [isin: tuleva2ndPillarStockFund().isin, value: 100, fundFee: 0.02, unavailableValue: 100],
        [isin: lhv2ndPillarFund().isin, value: 200, fundFee: 0.03, unavailableValue: 0.0]
    ]                                                                                                        | 0.0250
    [
        [isin: tuleva2ndPillarStockFund().isin, value: 0.0000001, fundFee: 0.000002, unavailableValue: 0.0],
        [isin: lhv2ndPillarFund().isin, value: 0.0000002, fundFee: 0.000003, unavailableValue: 0.0]
    ]                                                                                                        | 0.0
    [
        [isin: tuleva2ndPillarStockFund().isin, value: 0.0, fundFee: 0.01, unavailableValue: 0.0],
        [isin: lhv2ndPillarFund().isin, value: 0.0, fundFee: 0.02, unavailableValue: 0.0]
    ]                                                                                                        | 0.0
  }

  def "Calculates the 2nd pillar weighted average fee correctly with a pending exchange"() {
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


    def sourceFund = tuleva2ndPillarStockFund().tap { ongoingChargesFigure = 0.005 }
    def targetFund = lhv2ndPillarFund().tap { ongoingChargesFigure = 0.01 }
    def pendingExchanges = [new Exchange(
        new ApiFundResponse(sourceFund, locale),
        new ApiFundResponse(targetFund, locale),
        null,
        1.0 // 100%
    )]

    expect:
    weightedAverageFeeCalculator.getWeightedAverageFee(funds, pendingExchanges) == expectedWeightedAverageFee

    where:
    fundData                                                                                          | expectedWeightedAverageFee
    []                                                                                                | 0.0
    [
        [isin: tuleva2ndPillarStockFund().isin, value: 0.0, fundFee: 0.005, unavailableValue: 0.0]
    ]                                                                                                 | 0.0
    [
        [isin: tuleva2ndPillarStockFund().isin, value: 100.0, fundFee: 0.005, unavailableValue: 0.0]
    ]                                                                                                 | 0.01
    [
        [isin: tuleva2ndPillarStockFund().isin, value: 100.0, fundFee: 0.005, unavailableValue: 0.0],
        [isin: tuleva2ndPillarBondFund().isin, value: 200.0, fundFee: 0.006, unavailableValue: 0.0]
    ]                                                                                                 | ((200 * 0.006 + 100 * 0.01) / 300).round(4)
    [
        [isin: tuleva2ndPillarStockFund().isin, value: 100.0, fundFee: 0.005, unavailableValue: 100],
        [isin: tuleva2ndPillarBondFund().isin, value: 200.0, fundFee: 0.006, unavailableValue: 0.0]
    ]                                                                                                 | ((200 * 0.006 + 200 * 0.01) / 400).round(4)
  }

  def "Calculates the 3rd pillar weighted average fee correctly with a pending exchange"() {
    given:
    List<FundBalance> fundBalances = fundBalanceData.collect { fundInfo ->
      Fund fund = Fund.builder()
          .ongoingChargesFigure(fundInfo.fundFee)
          .isin(fundInfo.isin)
          .build()

      FundBalance.builder()
          .fund(fund)
          .value(fundInfo.value)
          .unavailableValue(fundInfo.unavailableValue)
          .units(fundInfo.units)
          .unavailableUnits(fundInfo.unavailableUnits)
          .build()
    }

    def locale = LocaleContextHolder.getLocale()


    def sourceFund = lhv3rdPillarFund().tap { ongoingChargesFigure = 0.01 }
    def targetFund = tuleva3rdPillarFund().tap { ongoingChargesFigure = 0.005 }
    def pendingExchanges = [new Exchange(
        new ApiFundResponse(sourceFund, locale),
        new ApiFundResponse(targetFund, locale),
        null,
        2345.6789 // 100% of bookValue
    )]

    expect:
    weightedAverageFeeCalculator.getWeightedAverageFee(fundBalances, pendingExchanges) == expectedWeightedAverageFee

    where:
    fundBalanceData                                                                                                                  | expectedWeightedAverageFee
    []                                                                                                                               | 0.0
    [[isin: lhv3rdPillarFund().isin, fundFee: 0.01, value: 0.0, unavailableValue: 4300.12, units: 0.0, unavailableUnits: 2345.6789]] | 0.005
  }

  def "Calculates the weighted average fee correctly with multiple pending exchanges"() {
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

    def sourceFund = tuleva2ndPillarStockFund().tap { ongoingChargesFigure = 0.005 }
    def targetFund1 = tuleva2ndPillarBondFund().tap { ongoingChargesFigure = 0.006 }
    def targetFund2 = lhv2ndPillarFund().tap { ongoingChargesFigure = 0.02 }
    def pendingExchanges = [
        new Exchange(
            new ApiFundResponse(sourceFund, locale),
            new ApiFundResponse(targetFund1, locale),
            null,
            0.5 // 50%
        ),
        new Exchange(
            new ApiFundResponse(sourceFund, locale),
            new ApiFundResponse(targetFund2, locale),
            null,
            0.5 // 50%
        )
    ]

    expect:
    weightedAverageFeeCalculator.getWeightedAverageFee(funds, pendingExchanges) == expectedWeightedAverageFee

    where:
    fundData                                                                                          | expectedWeightedAverageFee
    []                                                                                                | 0.0
    [
        [isin: tuleva2ndPillarStockFund().isin, value: 0.0, fundFee: 0.005, unavailableValue: 0.0]
    ]                                                                                                 | 0.0
    [
        [isin: tuleva2ndPillarStockFund().isin, value: 100.0, fundFee: 0.005, unavailableValue: 0.0]
    ]                                                                                                 | (50 * 0.006 + 50 * 0.02) / 100
    [
        [isin: tuleva2ndPillarStockFund().isin, value: 100.0, fundFee: 0.005, unavailableValue: 0.0],
        [isin: tuleva2ndPillarBondFund().isin, value: 100.0, fundFee: 0.006, unavailableValue: 0.0]
    ]                                                                                                 | (150 * 0.006 + 50 * 0.02) / 200
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

    def sourceFund = tuleva2ndPillarStockFund()
    def targetFund = null
    def exchange = new Exchange(
        new ApiFundResponse(sourceFund, locale),
        targetFund,
        "target PIK",
        1.0 // 100%
    )

    def pendingExchanges = [exchange]

    expect:
    weightedAverageFeeCalculator.getWeightedAverageFee(funds, pendingExchanges) == expectedWeightedAverageFee

    where:
    fundData                                                                                            | expectedWeightedAverageFee
    []                                                                                                  | 0.0
    [
        [isin: tuleva2ndPillarStockFund().isin, value: 0.0, fundFee: 0.005, unavailableValue: 0.0]
    ]                                                                                                   | 0.0
    [
        [isin: tuleva2ndPillarStockFund().isin, value: 100.0, fundFee: 0.005, unavailableValue: 0.0]
    ]                                                                                                   | 0.0
    [
        [isin: tuleva2ndPillarStockFund().isin, value: 100.0, fundFee: 0.005, unavailableValue: 0.0],
        [isin: lhv2ndPillarFund().isin, value: 200.0, fundFee: 0.01, unavailableValue: 0.0]
    ]                                                                                                   | 0.01
    [
        [isin: tuleva2ndPillarStockFund().isin, value: 100.0, fundFee: 0.005, unavailableValue: 100.0],
        [isin: lhv2ndPillarFund().isin, value: 200.0, fundFee: 0.01, unavailableValue: 0.0]
    ]                                                                                                   | 0.01
  }
}
