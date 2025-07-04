package ee.tuleva.onboarding.comparisons.returns.provider

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.CpiValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EpiIndex
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever
import ee.tuleva.onboarding.comparisons.overview.AccountOverview
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider
import ee.tuleva.onboarding.comparisons.overview.Transaction
import ee.tuleva.onboarding.comparisons.returns.ReturnCalculator
import ee.tuleva.onboarding.comparisons.returns.ReturnDto
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.INDEX
import static ee.tuleva.onboarding.currency.Currency.EUR

class IndexReturnProviderSpec extends Specification {

    def accountOverviewProvider = Mock(AccountOverviewProvider)
    def rateOfReturnCalculator = Mock(ReturnCalculator)

    def returnProvider = new IndexReturnProvider(accountOverviewProvider, rateOfReturnCalculator)

    def "can assemble a Returns object for EPI, EPI_3, UNION STOCK INDEX and CPI"() {
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
        rateOfReturnCalculator.getSimulatedReturn(overview, EpiIndex.EPI.key) >>
            new ReturnDto(expectedReturn, returnAsAmount, payments, EUR, earliestTransactionDate, LocalDate.now())
        rateOfReturnCalculator.getSimulatedReturn(overview, EpiIndex.EPI_3.key) >>
            new ReturnDto(expectedReturn, returnAsAmount, payments, EUR, earliestTransactionDate, LocalDate.now())
        rateOfReturnCalculator.getSimulatedReturn(overview, UnionStockIndexRetriever.KEY) >>
            new ReturnDto(expectedReturn, returnAsAmount, payments, EUR, earliestTransactionDate, LocalDate.now())
        rateOfReturnCalculator.getSimulatedReturn(overview, CpiValueRetriever.KEY) >>
            new ReturnDto(expectedReturn, returnAsAmount, payments, EUR, earliestTransactionDate, LocalDate.now())


        when:
        def returns = returnProvider.getReturns(
            new ReturnCalculationParameters(person, startTime, endTime, pillar, returnProvider.getKeys()))

        then:
        with(returns.returns[0]) {
          key == EpiIndex.EPI.key
          type == INDEX
          rate == expectedReturn
          amount == returnAsAmount
          paymentsSum == payments
          currency == EUR
          from == earliestTransactionDate
          to == LocalDate.now()
        }
        with(returns.returns[1]) {
          key == EpiIndex.EPI_3.key
          type == INDEX
          rate == expectedReturn
          amount == returnAsAmount
          paymentsSum == payments
          currency == EUR
          from == earliestTransactionDate
          to == LocalDate.now()
        }
        with(returns.returns[2]) {
          key == UnionStockIndexRetriever.KEY
          type == INDEX
          rate == expectedReturn
          amount == returnAsAmount
          paymentsSum == payments
          currency == EUR
          from == earliestTransactionDate
          to == LocalDate.now()
        }
        with(returns.returns[3]) {
          key == CpiValueRetriever.KEY
          type == INDEX
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
}
