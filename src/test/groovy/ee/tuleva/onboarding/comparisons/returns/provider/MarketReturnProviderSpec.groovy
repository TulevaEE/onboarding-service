package ee.tuleva.onboarding.comparisons.returns.provider

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.WorldIndexValueRetriever
import ee.tuleva.onboarding.comparisons.overview.AccountOverview
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider
import ee.tuleva.onboarding.comparisons.returns.RateOfReturnCalculator
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.INDEX

class MarketReturnProviderSpec extends Specification {

    def accountOverviewProvider = Mock(AccountOverviewProvider)
    def rateOfReturnCalculator = Mock(RateOfReturnCalculator)

    def returnProvider = new MarketReturnProvider(accountOverviewProvider, rateOfReturnCalculator)

    def "can assemble a Return object for world index"() {
        given:
        def person = samplePerson()
        def startTime = Instant.parse("2019-08-28T10:06:01Z")
        def endTime = Instant.now()
        def pillar = 2
        def overview = new AccountOverview([], 0.0, 0.0, startTime, endTime, 2)
        def expectedReturn = 0.00123.doubleValue()

        accountOverviewProvider.getAccountOverview(person, startTime, pillar) >> overview
        rateOfReturnCalculator.getRateOfReturn(overview, WorldIndexValueRetriever.KEY) >> expectedReturn

        when:
        def returns = returnProvider.getReturns(person, startTime, pillar)

        then:
        with(returns.returns[0]) {
            key == WorldIndexValueRetriever.KEY
            type == INDEX
            value == expectedReturn
        }
    }
}
