package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.epis.EpisService
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.epis.cashflows.CashFlowFixture.cashFlowFixture

class CashFlowServiceSpec extends Specification {

    def episService = Mock(EpisService)

    def service = new CashFlowService(episService)

    def "can get a cashflow statement"() {
        given:
        def person = samplePerson()
        def fromDate = service.BEGINNING_OF_TIME
        def toDate = LocalDate.now()
        def expectedStatement = cashFlowFixture()
        episService.getCashFlowStatement(person, fromDate, toDate) >> expectedStatement

        when:
        def statement = service.getCashFlowStatement(person)

        then:
        statement == expectedStatement
    }

    def "can get a cashflow statement for a specific date range"() {
        given:
        def person = samplePerson()
        def fromDate = LocalDate.parse("2010-01-01")
        def toDate = LocalDate.now()
        def expectedStatement = cashFlowFixture()
        episService.getCashFlowStatement(person, fromDate, toDate) >> expectedStatement

        when:
        def statement = service.getCashFlowStatement(person, fromDate, toDate)

        then:
        statement == expectedStatement
    }
}
