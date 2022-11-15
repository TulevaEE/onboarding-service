package ee.tuleva.onboarding.comparisons.returns.provider

import ee.tuleva.onboarding.comparisons.overview.AccountOverview
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider
import ee.tuleva.onboarding.comparisons.returns.ReturnCalculator
import ee.tuleva.onboarding.comparisons.returns.ReturnDto
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.PERSONAL
import static ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider.SECOND_PILLAR
import static ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider.THIRD_PILLAR
import static ee.tuleva.onboarding.currency.Currency.EUR

class PersonalReturnProviderSpec extends Specification {

    def accountOverviewProvider = Mock(AccountOverviewProvider)
    def rateOfReturnCalculator = Mock(ReturnCalculator)

    def returnProvider = new PersonalReturnProvider(accountOverviewProvider, rateOfReturnCalculator)

    def "can assemble a Return object for your personal 2nd pillar fund"() {
        given:
        def person = samplePerson()
        def startTime = Instant.parse("2019-08-28T10:06:01Z")
        def endTime = Instant.now()
        def pillar = 2
        def overview = new AccountOverview([], 0.0, 0.0, startTime, endTime, pillar)
        def expectedReturn = 0.00123
        def returnAsAmount = 123.12

        accountOverviewProvider.getAccountOverview(person, startTime, pillar) >> overview
        rateOfReturnCalculator.getReturn(overview) >>
            new ReturnDto(expectedReturn, returnAsAmount, EUR)

        when:
        def returns = returnProvider.getReturns(person, startTime, pillar)

        then:
        with(returns.returns[0]) {
          key == SECOND_PILLAR
          type == PERSONAL
          rate == expectedReturn
          amount == returnAsAmount
          currency == EUR
        }
    }

    def "can assemble a Returns object for your personal 3rd pillar fund"() {
        given:
        def person = samplePerson()
        def startTime = Instant.parse("2019-08-28T10:06:01Z")
        def endTime = Instant.now()
        def pillar = 3
        def overview = new AccountOverview([], 0.0, 0.0, startTime, endTime, pillar)
        def expectedReturn = 0.00123
        def returnAsAmount = 123.21

        accountOverviewProvider.getAccountOverview(person, startTime, pillar) >> overview
        rateOfReturnCalculator.getReturn(overview) >>
            new ReturnDto(expectedReturn, returnAsAmount, EUR)

        when:
        def returns = returnProvider.getReturns(person, startTime, pillar)

        then:
        with(returns.returns[0]) {
          key == THIRD_PILLAR
          type == PERSONAL
          rate == expectedReturn
          amount == returnAsAmount
          currency == EUR
        }
    }
}
