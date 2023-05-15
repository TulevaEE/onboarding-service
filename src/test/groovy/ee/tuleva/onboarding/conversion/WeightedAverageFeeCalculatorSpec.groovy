package ee.tuleva.onboarding.conversion

import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.account.FundBalance
import spock.lang.Specification
import spock.lang.Unroll

class WeightedAverageFeeCalculatorSpec extends Specification {

  WeightedAverageFeeCalculator weightedAverageFeeCalculator = new WeightedAverageFeeCalculator()

  @Unroll
  def "Calculates the weighted average fee correctly"() {
    given:
    List<FundBalance> funds = fundData.collect { fundInfo ->
      Fund fund = Fund.builder()
          .ongoingChargesFigure(fundInfo.ongoingChargesFigure)
          .build()

      FundBalance.builder()
          .fund(fund)
          .value(fundInfo.value)
          .unavailableValue(fundInfo.unavailableValue)
          .build()
    }

    expect:
    weightedAverageFeeCalculator.getWeightedAverageFee(funds) == expectedWeightedAverageFee

    where:
    fundData | expectedWeightedAverageFee
    [] | BigDecimal.ZERO
    [
        [value: new BigDecimal("0"), ongoingChargesFigure: new BigDecimal("0.01"), unavailableValue: BigDecimal.ZERO]
    ] | new BigDecimal("0.0100")
    [
        [value: new BigDecimal("100"), ongoingChargesFigure: new BigDecimal("0.01"), unavailableValue: BigDecimal.ZERO]
    ] | new BigDecimal("0.0100")
    [
        [value: new BigDecimal("100"), ongoingChargesFigure: new BigDecimal("0.02"), unavailableValue: BigDecimal.ZERO],
        [value: new BigDecimal("200"), ongoingChargesFigure: new BigDecimal("0.03"), unavailableValue: BigDecimal.ZERO]
    ] | new BigDecimal("0.0267")
    [
        [value: new BigDecimal("100"), ongoingChargesFigure: new BigDecimal("0.02"), unavailableValue: new BigDecimal("100")],
        [value: new BigDecimal("200"), ongoingChargesFigure: new BigDecimal("0.03"), unavailableValue: BigDecimal.ZERO]
    ] | new BigDecimal("0.0250")
    [
        [value: new BigDecimal("0.0000001"), ongoingChargesFigure: new BigDecimal("0.000002"), unavailableValue: BigDecimal.ZERO],
        [value: new BigDecimal("0.0000002"), ongoingChargesFigure: new BigDecimal("0.000003"), unavailableValue: BigDecimal.ZERO]
    ] | new BigDecimal("0.0000")
    [
        [value: new BigDecimal("0.0"), ongoingChargesFigure: new BigDecimal("0.01"), unavailableValue: BigDecimal.ZERO],
        [value: new BigDecimal("0.0"), ongoingChargesFigure: new BigDecimal("0.02"), unavailableValue: BigDecimal.ZERO]
    ] | new BigDecimal("0.02")
  }

}
