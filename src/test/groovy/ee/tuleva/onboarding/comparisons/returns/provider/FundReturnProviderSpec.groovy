package ee.tuleva.onboarding.comparisons.returns.provider


import ee.tuleva.onboarding.comparisons.overview.AccountOverview
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider
import ee.tuleva.onboarding.comparisons.returns.RateOfReturnCalculator
import ee.tuleva.onboarding.comparisons.returns.ReturnRateAndAmount
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.FUND

class FundReturnProviderSpec extends Specification {

    def accountOverviewProvider = Mock(AccountOverviewProvider)
    def rateOfReturnCalculator = Mock(RateOfReturnCalculator)

    def returnProvider = new FundReturnProvider(accountOverviewProvider, rateOfReturnCalculator)

    def "can assemble a Returns object for all funds"() {
        given:
        def person = samplePerson()
        def startTime = Instant.parse("2019-08-28T10:06:01Z")
        def endTime = Instant.now()
        def pillar = 2
        def overview = new AccountOverview([], 0.0, 0.0, startTime, endTime, pillar)
        def expectedReturn = 0.00123
        def returnAsAmount = 123.12

        accountOverviewProvider.getAccountOverview(person, startTime, pillar) >> overview
        rateOfReturnCalculator.getReturnRateAndAmount(overview, _ as String) >>
            new ReturnRateAndAmount(expectedReturn, returnAsAmount)

        when:
        def returns = returnProvider.getReturns(person, startTime, pillar)

        then:
        with(returns.returns[0]) {
          key == returnProvider.getKeys()[0]
          type == FUND
          rate == expectedReturn
          amount == returnAsAmount
        }
        returns.returns.size() == returnProvider.getKeys().size()
    }
}
