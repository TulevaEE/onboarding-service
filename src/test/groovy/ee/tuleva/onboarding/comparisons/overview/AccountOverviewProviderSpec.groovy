package ee.tuleva.onboarding.comparisons.overview

import ee.tuleva.onboarding.account.CashFlowService
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.epis.cashflows.CashFlowFixture.cashFlowFixture

class AccountOverviewProviderSpec extends Specification {

    FundRepository fundRepository
    CashFlowService cashFlowService
    AccountOverviewProvider accountOverviewProvider

    Person person = samplePerson()
    LocalDate startDate = LocalDate.parse("1998-01-01")
    Instant startTime = startDate.atStartOfDay().toInstant(ZoneOffset.UTC)
    Instant endTime = Instant.now()
    LocalDate endDate = LocalDateTime.ofInstant(endTime, ZoneOffset.UTC).toLocalDate()
    CashFlowStatement cashFlowStatement = cashFlowFixture()
    def pillar = 2

    def setup() {
        fundRepository = Mock(FundRepository)
        cashFlowService = Mock(CashFlowService)
        accountOverviewProvider = new AccountOverviewProvider(fundRepository, cashFlowService)
        fundRepository.findAllByPillar(pillar) >> [
            Fund.builder().isin("1").build(),
            Fund.builder().isin("2").build(),
        ]
        cashFlowService.getCashFlowStatement(person, startDate, endDate) >> cashFlowStatement
    }

    def "it sets the right start and end times"() {
        when:
        AccountOverview accountOverview = accountOverviewProvider.getAccountOverview(person, startTime, pillar)
        then:
        accountOverview.startTime == startTime
        verifyTimeCloseToNow(accountOverview.endTime)
    }

    def "it bunches together and converts the starting and ending balance"() {
        when:
        AccountOverview accountOverview = accountOverviewProvider.getAccountOverview(person, startTime, pillar)
        then:
        accountOverview.beginningBalance == 1000.00 + 115.00
        accountOverview.endingBalance == 1100.00 + 125.00
    }

    def "it converts all transactions"() {
        when:
        AccountOverview accountOverview = accountOverviewProvider.getAccountOverview(person, startTime, pillar)
        then:
        accountOverview.pillar == pillar
        accountOverview.transactions.size() == 2
        accountOverview.transactions[0].amount == cashFlowStatement.transactions[0].amount
        accountOverview.transactions[0].date == cashFlowStatement.transactions[0].date
        accountOverview.transactions[1].amount == cashFlowStatement.transactions[1].amount
        accountOverview.transactions[1].date == cashFlowStatement.transactions[1].date
    }

    private static boolean verifyTimeCloseToNow(Instant time) {
        return time.epochSecond > (Instant.now().epochSecond - 100)
    }
}
