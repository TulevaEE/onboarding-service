package ee.tuleva.onboarding.comparisons.returns.provider


import ee.tuleva.onboarding.comparisons.overview.AccountOverview
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider
import ee.tuleva.onboarding.comparisons.overview.Transaction
import ee.tuleva.onboarding.comparisons.returns.ReturnCalculator
import ee.tuleva.onboarding.comparisons.returns.ReturnDto
import ee.tuleva.onboarding.comparisons.returns.Returns
import ee.tuleva.onboarding.fund.FundRepository
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.FUND
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarBondFund
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarStockFund

class FundReturnProviderSpec extends Specification {

  AccountOverviewProvider accountOverviewProvider = Mock()
  ReturnCalculator rateOfReturnCalculator = Mock()
  FundRepository fundRepository = Mock()

  def returnProvider = new FundReturnProvider(accountOverviewProvider, rateOfReturnCalculator, fundRepository)

  def "can assemble a Returns object for all funds"() {
    given:
    def person = samplePerson()
    def startTime = Instant.parse("2019-08-28T10:06:01Z")
    def endTime = Instant.now()
    def pillar = 2
    def earliestTransactionDate = LocalDate.parse("2020-09-10")
    def overview = new AccountOverview(
        [new Transaction(10.0, Instant.parse("2020-10-11T10:06:01Z")),
         new Transaction(100.0, Instant.parse("${earliestTransactionDate}T10:06:01Z"))],
        0.0, 0.0, startTime, endTime, pillar)
    def expectedReturn = 0.00123
    def returnAsAmount = 123.12
    def payments = 234.12

    accountOverviewProvider.getAccountOverview(person, startTime, endTime, pillar) >> overview
    rateOfReturnCalculator.getSimulatedReturn(overview, _ as String) >>
        new ReturnDto(expectedReturn, returnAsAmount, payments, EUR, earliestTransactionDate, LocalDate.now())
    fundRepository.findAllByStatus(ACTIVE) >> List.of(tuleva2ndPillarBondFund(), tuleva2ndPillarStockFund())

    when:
    Returns returns = returnProvider.getReturns(
        new ReturnCalculationParameters(person, startTime, endTime, pillar, returnProvider.getKeys()))

    then:
    with(returns.returns[0]) {
      key == returnProvider.getKeys()[0]
      type == FUND
      rate == expectedReturn
      amount == returnAsAmount
      paymentsSum == payments
      currency == EUR
      from == earliestTransactionDate
      to == LocalDate.now()
    }
    returns.returns.size() == returnProvider.getKeys().size()
    returns.from == earliestTransactionDate
  }

  def "assemble Returns for only specified keys"() {
    given:
        def person = samplePerson()
        def startTime = Instant.parse("2019-08-28T10:06:01Z")
        def endTime = Instant.now()
        def pillar = 2
        def earliestTransactionDate = LocalDate.parse("2020-09-10")
        def overview = new AccountOverview(
            [new Transaction(10.0, Instant.parse("2020-10-11T10:06:01Z")),
             new Transaction(100.0, Instant.parse("${earliestTransactionDate}T10:06:01Z"))],
            0.0, 0.0, startTime, endTime, pillar)
        def expectedReturn = 0.00123
        def returnAsAmount = 123.12
        def payments = 234.12

        accountOverviewProvider.getAccountOverview(person, startTime, endTime, pillar) >> overview
        rateOfReturnCalculator.getSimulatedReturn(overview, _ as String) >>
            new ReturnDto(expectedReturn, returnAsAmount, payments, EUR, earliestTransactionDate, LocalDate.now())
        fundRepository.findAllByStatus(ACTIVE) >> List.of(tuleva2ndPillarBondFund(), tuleva2ndPillarStockFund())

        def specifiedKeys = [tuleva2ndPillarStockFund().isin]

    when:
        Returns returns = returnProvider.getReturns(
            new ReturnCalculationParameters(person, startTime, endTime, pillar, specifiedKeys))

    then:
        with(returns.returns[0]) {
          key == specifiedKeys[0]
          type == FUND
          rate == expectedReturn
          amount == returnAsAmount
          paymentsSum == payments
          currency == EUR
          from == earliestTransactionDate
          to == LocalDate.now()
        }
        returns.returns.size() == specifiedKeys.size()
        returns.from == earliestTransactionDate
  }

}
