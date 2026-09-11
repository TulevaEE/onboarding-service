package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.conversion.ConversionCashFlow
import ee.tuleva.onboarding.epis.CashFlow
import ee.tuleva.onboarding.epis.CashFlowStatement
import ee.tuleva.onboarding.fund.FundRepository
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.epis.CashFlow.Type.*
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarStockFund
import static ee.tuleva.onboarding.fund.FundFixture.tuleva3rdPillarFund

class AccountConversionCashFlowsSpec extends Specification {

  def cashFlowService = Mock(CashFlowService)
  def fundRepository = Mock(FundRepository)
  def conversionCashFlows = new AccountConversionCashFlows(cashFlowService, fundRepository)

  def secondPillarIsin = tuleva2ndPillarStockFund().isin
  def thirdPillarIsin = tuleva3rdPillarFund().isin

  def "classifies contribution and subtraction cash flows into conversion cash flows, whole record"() {
    given:
    fundRepository.findByIsin(secondPillarIsin) >> tuleva2ndPillarStockFund()
    fundRepository.findByIsin(thirdPillarIsin) >> tuleva3rdPillarFund()

    def cashContributionTime = Instant.parse("2019-11-20T00:00:00Z")
    def contributionTime = Instant.parse("2019-12-20T00:00:00Z")
    def contributionPriceTime = Instant.parse("2019-12-21T00:00:00Z")
    def subtractionTime = Instant.parse("2019-12-22T00:00:00Z")

    cashFlowService.getCashFlowStatement(samplePerson) >> CashFlowStatement.builder()
        .transactions([
            CashFlow.builder().isin(secondPillarIsin).time(cashContributionTime).amount(100.0).type(CONTRIBUTION_CASH).build(),
            CashFlow.builder().isin(thirdPillarIsin).time(contributionTime).priceTime(contributionPriceTime).amount(50.0).type(CONTRIBUTION).build(),
            CashFlow.builder().isin(secondPillarIsin).time(subtractionTime).amount(20.0).type(SUBTRACTION).build(),
            CashFlow.builder().isin(null).time(subtractionTime).amount(5.0).type(CASH).build(),
            CashFlow.builder().isin(thirdPillarIsin).time(subtractionTime).amount(1.0).type(OTHER).build(),
        ])
        .build()

    when:
    List<ConversionCashFlow> result = conversionCashFlows.forPerson(samplePerson)

    then:
    result == [
        new ConversionCashFlow(2, 100.0, cashContributionTime, true, true, false),
        new ConversionCashFlow(3, 50.0, contributionPriceTime, false, true, false),
        new ConversionCashFlow(2, 20.0, subtractionTime, false, false, true),
    ]
  }

  def "skips contribution and subtraction cash flows whose isin does not resolve to a known fund"() {
    given:
    fundRepository.findByIsin(secondPillarIsin) >> tuleva2ndPillarStockFund()
    fundRepository.findByIsin("UNKNOWN") >> null

    def time = Instant.parse("2019-11-20T00:00:00Z")

    cashFlowService.getCashFlowStatement(samplePerson) >> CashFlowStatement.builder()
        .transactions([
            CashFlow.builder().isin("UNKNOWN").time(time).amount(100.0).type(SUBTRACTION).build(),
            CashFlow.builder().isin(secondPillarIsin).time(time).amount(20.0).type(SUBTRACTION).build(),
        ])
        .build()

    when:
    List<ConversionCashFlow> result = conversionCashFlows.forPerson(samplePerson)

    then:
    result == [
        new ConversionCashFlow(2, 20.0, time, false, false, true),
    ]
  }

  def "fails fast when a cash contribution references an unknown fund"() {
    given:
    fundRepository.findByIsin("UNKNOWN") >> null

    cashFlowService.getCashFlowStatement(samplePerson) >> CashFlowStatement.builder()
        .transactions([
            CashFlow.builder().isin("UNKNOWN").time(Instant.parse("2019-11-20T00:00:00Z")).amount(100.0).type(CONTRIBUTION_CASH).build(),
        ])
        .build()

    when:
    conversionCashFlows.forPerson(samplePerson)

    then:
    thrown(IllegalStateException)
  }
}
