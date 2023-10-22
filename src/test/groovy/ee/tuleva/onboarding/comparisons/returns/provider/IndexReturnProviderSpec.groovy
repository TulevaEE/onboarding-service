package ee.tuleva.onboarding.comparisons.returns.provider

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.CPIValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EPIFundValueRetriever
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

    def "can assemble a Returns object for EPI, UNION STOCK INDEX and CPI"() {
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

        accountOverviewProvider.getAccountOverview(person, startTime, pillar) >> overview
        rateOfReturnCalculator.getReturn(overview, EPIFundValueRetriever.KEY) >>
            new ReturnDto(expectedReturn, returnAsAmount, EUR, earliestTransactionDate)
        rateOfReturnCalculator.getReturn(overview, UnionStockIndexRetriever.KEY) >>
            new ReturnDto(expectedReturn, returnAsAmount, EUR, earliestTransactionDate)
        rateOfReturnCalculator.getReturn(overview, CPIValueRetriever.KEY) >>
            new ReturnDto(expectedReturn, returnAsAmount, EUR, earliestTransactionDate)


        when:
        def returns = returnProvider.getReturns(person, startTime, pillar)

        then:
        with(returns.returns[0]) {
          key == EPIFundValueRetriever.KEY
          type == INDEX
          rate == expectedReturn
          amount == returnAsAmount
          currency == EUR
          from == earliestTransactionDate
        }
        with(returns.returns[1]) {
          key == UnionStockIndexRetriever.KEY
          type == INDEX
          rate == expectedReturn
          currency == EUR
          from == earliestTransactionDate
        }
        with(returns.returns[2]) {
          key == CPIValueRetriever.KEY
          type == INDEX
          rate == expectedReturn
          currency == EUR
          from == earliestTransactionDate
        }
        returns.returns.size() == returnProvider.getKeys().size()
        returns.from == earliestTransactionDate
    }
}
