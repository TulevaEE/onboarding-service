package ee.tuleva.onboarding.comparisons.returns.provider

import ee.tuleva.onboarding.comparisons.overview.AccountOverview
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider
import ee.tuleva.onboarding.comparisons.overview.Transaction
import ee.tuleva.onboarding.comparisons.returns.ReturnCalculator
import ee.tuleva.onboarding.comparisons.returns.ReturnDto
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.PERSONAL
import static ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider.SECOND_PILLAR
import static ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider.THIRD_PILLAR
import static ee.tuleva.onboarding.currency.Currency.EUR

class PersonalReturnProviderSpec extends Specification {

    def accountOverviewProvider = Mock(AccountOverviewProvider)
    def rateOfReturnCalculator = Mock(ReturnCalculator)

    def returnProvider = new PersonalReturnProvider(accountOverviewProvider, rateOfReturnCalculator)

    def "can assemble a Returns object for your personal 2nd pillar fund"() {
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

        accountOverviewProvider.getAccountOverview(person, startTime, pillar) >> overview
        rateOfReturnCalculator.getReturn(overview) >>
            new ReturnDto(expectedReturn, returnAsAmount, payments, EUR, earliestTransactionDate)

        when:
        def returns = returnProvider.getReturns(
            new ReturnCalculationParameters(person, startTime, pillar, returnProvider.getKeys()))

        then:
        with(returns.returns[0]) {
          key == SECOND_PILLAR
          type == PERSONAL
          rate == expectedReturn
          amount == returnAsAmount
          paymentsSum == payments
          currency == EUR
          from == earliestTransactionDate
        }
        returns.returns.size() == 1
        returns.from == earliestTransactionDate
    }

    def "can assemble a Returns object for your personal 3rd pillar fund"() {
        given:
        def person = samplePerson()
        def startTime = Instant.parse("2019-08-28T10:06:01Z")
        def endTime = Instant.now()
        def pillar = 3
        def earliestTransactionDate = LocalDate.parse("2020-09-10")
        def overview = new AccountOverview(
            [new Transaction(100.0, Instant.parse("${earliestTransactionDate}T10:06:01Z"))],
            0.0, 0.0, startTime, endTime, pillar)
        def expectedReturn = 0.00123
        def returnAsAmount = 123.21
        def payments = 234.45

        accountOverviewProvider.getAccountOverview(person, startTime, pillar) >> overview
        rateOfReturnCalculator.getReturn(overview) >>
            new ReturnDto(expectedReturn, returnAsAmount, payments, EUR, earliestTransactionDate)

        when:
        def returns = returnProvider.getReturns(
            new ReturnCalculationParameters(person, startTime, pillar, returnProvider.getKeys()))

        then:
        with(returns.returns[0]) {
          key == THIRD_PILLAR
          type == PERSONAL
          rate == expectedReturn
          amount == returnAsAmount
          paymentsSum == payments
          currency == EUR
          from == earliestTransactionDate
        }
        returns.returns.size() == 1
        returns.from == earliestTransactionDate
    }

    def "unknown pillar results in an exception"() {
    given:
        def person = samplePerson()
        def startTime = Instant.parse("2019-08-28T10:06:01Z")
        def endTime = Instant.now()
        def pillar = 1
        def earliestTransactionDate = LocalDate.parse("2020-09-10")
        def overview = new AccountOverview(
            [new Transaction(100.0, Instant.parse("${earliestTransactionDate}T10:06:01Z"))],
            0.0, 0.0, startTime, endTime, pillar)
        def expectedReturn = 0.00123
        def returnAsAmount = 123.21
        def payments = 234.45

        accountOverviewProvider.getAccountOverview(person, startTime, pillar) >> overview
        rateOfReturnCalculator.getReturn(overview) >>
            new ReturnDto(expectedReturn, returnAsAmount, payments, EUR, earliestTransactionDate)

    when:
        returnProvider.getReturns(
            new ReturnCalculationParameters(person, startTime, pillar, returnProvider.getKeys()))

    then:
        thrown(IllegalArgumentException)
  }

}
