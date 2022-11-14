package ee.tuleva.onboarding.comparisons.returns.provider

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.CPIValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EPIFundValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever
import ee.tuleva.onboarding.comparisons.overview.AccountOverview
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider
import ee.tuleva.onboarding.comparisons.returns.RateOfReturnCalculator
import ee.tuleva.onboarding.comparisons.returns.ReturnRateAndAmount
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.INDEX
import static ee.tuleva.onboarding.currency.Currency.EUR

class IndexReturnProviderSpec extends Specification {

    def accountOverviewProvider = Mock(AccountOverviewProvider)
    def rateOfReturnCalculator = Mock(RateOfReturnCalculator)

    def returnProvider = new IndexReturnProvider(accountOverviewProvider, rateOfReturnCalculator)

    def "can assemble a Returns object for EPI, UNION STOCK INDEX and CPI"() {
        given:
        def person = samplePerson()
        def startTime = Instant.parse("2019-08-28T10:06:01Z")
        def endTime = Instant.now()
        def pillar = 2
        def overview = new AccountOverview([], 0.0, 0.0, startTime, endTime, pillar)
        def expectedReturn = 0.00123
        def returnAsAmount = 123.12

        accountOverviewProvider.getAccountOverview(person, startTime, pillar) >> overview
        rateOfReturnCalculator.getReturn(overview, EPIFundValueRetriever.KEY) >>
            new ReturnRateAndAmount(expectedReturn, returnAsAmount, EUR)
        rateOfReturnCalculator.getReturn(overview, UnionStockIndexRetriever.KEY) >>
            new ReturnRateAndAmount(expectedReturn, returnAsAmount, EUR)
        rateOfReturnCalculator.getReturn(overview, CPIValueRetriever.KEY) >>
            new ReturnRateAndAmount(expectedReturn, returnAsAmount, EUR)


        when:
        def returns = returnProvider.getReturns(person, startTime, pillar)

        then:
        with(returns.returns[0]) {
          key == EPIFundValueRetriever.KEY
          type == INDEX
          rate == expectedReturn
          amount == returnAsAmount
          currency == EUR
        }
        with(returns.returns[1]) {
          key == UnionStockIndexRetriever.KEY
          type == INDEX
          rate == expectedReturn
          currency == EUR
        }
        with(returns.returns[2]) {
          key == CPIValueRetriever.KEY
          type == INDEX
          rate == expectedReturn
          currency == EUR
        }
    }
}
