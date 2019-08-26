package ee.tuleva.onboarding.comparisons.overview

import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement
import ee.tuleva.onboarding.epis.fund.FundDto
import spock.lang.Specification

import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

import static ee.tuleva.onboarding.epis.cashflows.CashFlowFixture.cashFlowFixture
import static ee.tuleva.onboarding.epis.fund.FundDto.FundStatus.ACTIVE

class EpisAccountOverviewProviderSpec extends Specification {

    EpisService episService
    EpisAccountOverviewProvider episAccountOverviewProvider

    def setup() {
        episService = Mock(EpisService)
        episAccountOverviewProvider = new EpisAccountOverviewProvider(episService)
        episService.getFunds() >> [
            new FundDto("1", "Fund 1", "TUK75", 2, ACTIVE),
            new FundDto("2", "Fund 2", "TUK75", 2, ACTIVE),
            new FundDto("3", "Fund 3", "TUK75", 3, ACTIVE)
        ]
    }

    def "it gets an account overview for the right person and time"() {
        given:
            Person person = Mock(Person)
            person.getPersonalCode() >> "test"
            LocalDate startDate = LocalDate.parse("1998-01-01")
            Instant startTime = startDate.atStartOfDay().toInstant(ZoneOffset.UTC)
        when:
            episAccountOverviewProvider.getAccountOverview(person, startTime, 2)
        then:
            1 * episService.getCashFlowStatement(person, startDate, LocalDate.now()) >> cashFlowFixture()
    }

    def "it sets the right start and end times"() {
        when:
            LocalDate startDate = LocalDate.parse("1998-01-01")
            Instant startTime = startDate.atStartOfDay().toInstant(ZoneOffset.UTC)
            AccountOverview accountOverview = episAccountOverviewProvider.getAccountOverview(null, startTime, 2)
        then:
            1 * episService.getCashFlowStatement(_, _, _) >> cashFlowFixture()
            accountOverview.startTime == startTime
            verifyTimeCloseToNow(accountOverview.endTime)
    }

    def "it bunches together and converts the starting balance"() {
        when:
            AccountOverview accountOverview = episAccountOverviewProvider.getAccountOverview(null, null, 2)
        then:
            1 * episService.getCashFlowStatement(_, _, _) >> cashFlowFixture()
            roundToTwoPlaces(accountOverview.beginningBalance) == 178.91
    }

    def "it bunches together and converts the ending balance"() {
        when:
            AccountOverview accountOverview = episAccountOverviewProvider.getAccountOverview(null, null, 2)
        then:
            1 * episService.getCashFlowStatement(_, _, _) >> cashFlowFixture()
            roundToTwoPlaces(accountOverview.endingBalance) == 195.30
    }

    def "it converts all transactions"() {
        given:
            CashFlowStatement cashFlow = cashFlowFixture()
            1 * episService.getCashFlowStatement(_, _, _) >> cashFlow
        when:
            AccountOverview accountOverview = episAccountOverviewProvider.getAccountOverview(null, null, 2)
        then:
            accountOverview.transactions.size() == 2
            accountOverview.pillar == 2
            roundToTwoPlaces(accountOverview.transactions[0].amount) == 6.39
            accountOverview.transactions[0].date == cashFlow.transactions[0].date
            accountOverview.transactions[1].amount == -cashFlow.transactions[1].amount
            accountOverview.transactions[1].date == cashFlow.transactions[1].date

    }

    private static BigDecimal roundToTwoPlaces(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("##.00")
        format.setParseBigDecimal(true)
        return new BigDecimal(format.format(value))
    }

    private static boolean verifyTimeCloseToNow(Instant time) {
        return time.epochSecond > (Instant.now().epochSecond - 100)
    }
}
