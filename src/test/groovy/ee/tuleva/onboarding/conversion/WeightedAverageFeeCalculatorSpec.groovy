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
          .ongoingChargesFigure(fundInfo.ongoingChargesFigure)
          .isin(tuleva2ndPillarStockFund.isin)
          .build()

      FundBalance.builder()
          .fund(fund)
          .value(fundInfo.value)
          .unavailableValue(fundInfo.unavailableValue)
          .build()
    }

    expect:
    weightedAverageFeeCalculator.getWeightedAverageFee(funds, []) == expectedWeightedAverageFee

    where:
    fundData                                                                                                                       | expectedWeightedAverageFee
    []                                                                                                                             | BigDecimal.ZERO
    [
        [value: new BigDecimal("0"), ongoingChargesFigure: new BigDecimal("0.01"), unavailableValue: BigDecimal.ZERO]
    ]                                                                                                                              | new BigDecimal("0.0100")
    [
        [value: new BigDecimal("100"), ongoingChargesFigure: new BigDecimal("0.01"), unavailableValue: BigDecimal.ZERO]
    ]                                                                                                                              | new BigDecimal("0.0100")
    [
        [value: new BigDecimal("100"), ongoingChargesFigure: new BigDecimal("0.02"), unavailableValue: BigDecimal.ZERO],
        [value: new BigDecimal("200"), ongoingChargesFigure: new BigDecimal("0.03"), unavailableValue: BigDecimal.ZERO]
    ]                                                                                                                              | new BigDecimal("0.0267")
    [
        [value: new BigDecimal("100"), ongoingChargesFigure: new BigDecimal("0.02"), unavailableValue: new BigDecimal("100")],
        [value: new BigDecimal("200"), ongoingChargesFigure: new BigDecimal("0.03"), unavailableValue: BigDecimal.ZERO]
    ]                                                                                                                              | new BigDecimal("0.0250")
    [
        [value: new BigDecimal("0.0000001"), ongoingChargesFigure: new BigDecimal("0.000002"), unavailableValue: BigDecimal.ZERO],
        [value: new BigDecimal("0.0000002"), ongoingChargesFigure: new BigDecimal("0.000003"), unavailableValue: BigDecimal.ZERO]
    ]                                                                                                                              | new BigDecimal("0.0000")
    [
        [value: new BigDecimal("0.0"), ongoingChargesFigure: new BigDecimal("0.01"), unavailableValue: BigDecimal.ZERO],
        [value: new BigDecimal("0.0"), ongoingChargesFigure: new BigDecimal("0.02"), unavailableValue: BigDecimal.ZERO]
    ]                                                                                                                              | new BigDecimal("0.02")
  }

  def "Calculates the weighted average fee correctly with pending exchanges"() {
    given:
    List<FundBalance> funds = fundData.collect { fundInfo ->
      Fund fund = Fund.builder()
          .ongoingChargesFigure(fundInfo.sourceFundFee)
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
        10.0
    )]

    expect:
    weightedAverageFeeCalculator.getWeightedAverageFee(funds, pendingExchanges) == expectedWeightedAverageFee

    where:
    fundData                                                                                             | expectedWeightedAverageFee
    []                                                                                                   | 0.0
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 0.0, sourceFundFee: 0.02, unavailableValue: 0.0]
    ]                                                                                                    | 0.01
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 100.0, sourceFundFee: 0.01, unavailableValue: 0.0]
    ]                                                                                                    | 0.01
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 100.0, sourceFundFee: 0.02, unavailableValue: 0.0],
        [isin: tuleva2ndPillarBondFund.isin, value: 200.0, sourceFundFee: 0.03, unavailableValue: 0.0]
    ]                                                                                                    | ((90 * 0.02 + 200 * 0.03 + 10 * 0.01) / 300).round(4)
    [
        [isin: tuleva2ndPillarStockFund.isin, value: 100.0, sourceFundFee: 0.02, unavailableValue: 100],
        [isin: tuleva2ndPillarBondFund.isin, value: 200.0, sourceFundFee: 0.03, unavailableValue: 0.0]
    ]                                                                                                    | ((190 * 0.02 + 200 * 0.03 + 10 * 0.01) / 400).round(4)
  }

  def "Calculates the weighted average fee correctly with pending pik exchanges"() {
    given:
    List<FundBalance> funds = fundData.collect { fundInfo ->
      Fund fund = Fund.builder()
          .ongoingChargesFigure(fundInfo.ongoingChargesFigure)
          .isin(tuleva2ndPillarStockFund.isin)
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
        BigDecimal.TEN
    )

    def exchanges = List.of(exchange)

    expect:
    weightedAverageFeeCalculator.getWeightedAverageFee(funds, exchanges) == expectedWeightedAverageFee

    where:
    fundData                                                                                                                   | expectedWeightedAverageFee
    []                                                                                                                         | BigDecimal.ZERO
    [
        [value: new BigDecimal("0"), ongoingChargesFigure: new BigDecimal("0.02"), unavailableValue: BigDecimal.ZERO]
    ]                                                                                                                          | new BigDecimal("0")
    [
        [value: new BigDecimal("100"), ongoingChargesFigure: new BigDecimal("0.01"), unavailableValue: BigDecimal.ZERO]
    ]                                                                                                                          | new BigDecimal("0.0090")
    [
        [value: new BigDecimal("100"), ongoingChargesFigure: new BigDecimal("0.02"), unavailableValue: BigDecimal.ZERO],
        [value: new BigDecimal("200"), ongoingChargesFigure: new BigDecimal("0.03"), unavailableValue: BigDecimal.ZERO]
    ]                                                                                                                          | new BigDecimal("0.0250")
    [
        [value: new BigDecimal("100"), ongoingChargesFigure: new BigDecimal("0.02"), unavailableValue: new BigDecimal("100")],
        [value: new BigDecimal("200"), ongoingChargesFigure: new BigDecimal("0.03"), unavailableValue: BigDecimal.ZERO]
    ]                                                                                                                          | new BigDecimal("0.0238")
  }
}
