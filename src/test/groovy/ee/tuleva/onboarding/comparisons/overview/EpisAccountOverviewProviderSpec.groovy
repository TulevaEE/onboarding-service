package ee.tuleva.onboarding.comparisons.overview

import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatementDto
import ee.tuleva.onboarding.epis.cashflows.CashFlowValueDto
import spock.lang.Specification

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.time.Instant

class EpisAccountOverviewProviderSpec extends Specification {

    EpisService episService
    EpisAccountOverviewProvider episAccountOverviewProvider

    void setup() {
        episService = Mock(EpisService)
        episAccountOverviewProvider = new EpisAccountOverviewProvider(episService)
    }

    def "it gets an account overview for the right person and time"() {
        given:
            Person person = Mock(Person)
            person.getPersonalCode() >> "test"
            Instant startTime = parseInstant("1998-01-01")
        when:
            episAccountOverviewProvider.getAccountOverview(person, startTime, 2)
        then:
            1 * episService.getCashFlowStatement(person, startTime, { verifyTimeCloseToNow(it) }) >> getFakeCashFlowStatement()
    }

    def "it sets the right start and end times"() {
        when:
            Instant startTime = parseInstant("1998-01-01")
            AccountOverview accountOverview = episAccountOverviewProvider.getAccountOverview(null, startTime, 2)
        then:
            1 * episService.getCashFlowStatement(_, _, _) >> getFakeCashFlowStatement()
            accountOverview.startTime == startTime
            verifyTimeCloseToNow(accountOverview.endTime)
    }

    def "it bunches together and converts the starting balance"() {
        when:
            AccountOverview accountOverview = episAccountOverviewProvider.getAccountOverview(null, null, 2)
        then:
            1 * episService.getCashFlowStatement(_, _, _) >> getFakeCashFlowStatement()
            roundToTwoPlaces(accountOverview.beginningBalance) == 178.91
    }

    def "it bunches together and converts the ending balance"() {
        when:
            AccountOverview accountOverview = episAccountOverviewProvider.getAccountOverview(null, null, 2)
        then:
            1 * episService.getCashFlowStatement(_, _, _) >> getFakeCashFlowStatement()
            roundToTwoPlaces(accountOverview.endingBalance) == 195.30
    }

    def "it converts all transactions"() {
        when:
            AccountOverview accountOverview = episAccountOverviewProvider.getAccountOverview(null, null, 2)
        then:
            1 * episService.getCashFlowStatement(_, _, _) >> getFakeCashFlowStatement()
            accountOverview.transactions.size() == 2
            roundToTwoPlaces(accountOverview.transactions[0].amount) == 6.39
            accountOverview.transactions[0].createdAt == getFakeCashFlowStatement().transactions[0].time
            accountOverview.transactions[1].amount == -getFakeCashFlowStatement().transactions[1].amount
            accountOverview.transactions[1].createdAt == getFakeCashFlowStatement().transactions[1].time
    }

    private static BigDecimal roundToTwoPlaces(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("##.00")
        format.setParseBigDecimal(true)
        return new BigDecimal(format.format(value))
    }

    private static CashFlowStatementDto getFakeCashFlowStatement() {
        Instant randomTime = parseInstant("2001-01-01")
        CashFlowStatementDto cashFlowStatementDto = CashFlowStatementDto.builder()
            .startBalance([
                "1": CashFlowValueDto.builder().time(randomTime).amount(1000.0).currency("EEK").pillar(2).build(),
                "2": CashFlowValueDto.builder().time(randomTime).amount(115.0).currency("EUR").pillar(2).build(),
                "3": CashFlowValueDto.builder().time(randomTime).amount(225.0).currency("EUR").pillar(3).build(),

            ])
            .endBalance([
                "1": CashFlowValueDto.builder().time(randomTime).amount(1100.0).currency("EEK").pillar(2).build(),
                "2": CashFlowValueDto.builder().time(randomTime).amount(125.0).currency("EUR").pillar(2).build(),
                "3": CashFlowValueDto.builder().time(randomTime).amount(250.0).currency("EUR").pillar(3).build(),
            ])
            .transactions([
                CashFlowValueDto.builder().time(randomTime).amount(-100.0).currency("EEK").pillar(2).build(),
                CashFlowValueDto.builder().time(randomTime).amount(-20.0).currency("EUR").pillar(2).build(),
                CashFlowValueDto.builder().time(randomTime).amount(-25.0).currency("EUR").pillar(3).build(),
            ]).build()
        return cashFlowStatementDto
    }

    private static boolean verifyTimeCloseToNow(Instant time) {
        return time.epochSecond > (Instant.now().epochSecond - 100)
    }

    private static Instant parseInstant(String format) {
        return new SimpleDateFormat("yyyy-MM-dd").parse(format).toInstant();
    }
}
