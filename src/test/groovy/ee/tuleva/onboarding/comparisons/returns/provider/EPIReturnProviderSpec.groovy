package ee.tuleva.onboarding.comparisons.returns.provider

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EPIFundValueRetriever
import ee.tuleva.onboarding.comparisons.overview.AccountOverview
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider
import ee.tuleva.onboarding.comparisons.returns.RateOfReturnCalculator
import ee.tuleva.onboarding.comparisons.returns.provider.EPIReturnProvider
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.INDEX

class EPIReturnProviderSpec extends Specification {

    def accountOverviewProvider = Mock(AccountOverviewProvider)
    def rateOfReturnCalculator = Mock(RateOfReturnCalculator)

    def returnProvider = new EPIReturnProvider(accountOverviewProvider, rateOfReturnCalculator)

    def "can assemble a Return object for EPI"() {
        given:
        def person = samplePerson()
        def startTime = Instant.parse("2019-08-28T10:06:01Z")
        def endTime = Instant.now()
        def pillar = 2
        def overview = new AccountOverview([], 0.0, 0.0, startTime, endTime, 2)
        def expectedReturn = 0.00123.doubleValue()

        accountOverviewProvider.getAccountOverview(person, startTime, pillar) >> overview
        rateOfReturnCalculator.getRateOfReturn(overview, EPIFundValueRetriever.KEY) >> expectedReturn

        when:
        def returns = returnProvider.getReturns(person, startTime, pillar)

        then:
        with(returns.returns[0]) {
            key == EPIFundValueRetriever.KEY
            type == INDEX
            value == expectedReturn
        }
    }
}
