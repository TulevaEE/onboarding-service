package ee.tuleva.onboarding.capital

import ee.tuleva.onboarding.capital.event.AggregatedCapitalEvent
import ee.tuleva.onboarding.capital.event.AggregatedCapitalEventRepository
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventRepository
import ee.tuleva.onboarding.user.member.Member
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventFixture.memberCapitalEventFixture
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.*
import static ee.tuleva.onboarding.capital.event.organisation.OrganisationCapitalEventType.INVESTMENT_RETURN
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.user.MemberFixture.memberFixture

class CapitalServiceSpec extends Specification {
    MemberCapitalEventRepository memberCapitalEventRepository = Mock()
    AggregatedCapitalEventRepository aggregatedCapitalEventRepository = Mock()
    CapitalService service = new CapitalService(memberCapitalEventRepository, aggregatedCapitalEventRepository)

    def "GetCapitalStatement"() {
        given:
        Member member = memberFixture().build()
        def event1 = memberCapitalEventFixture(member).type(CAPITAL_PAYMENT).fiatValue(1000.00)
            .ownershipUnitAmount(1000.00).build()
        def event2 = memberCapitalEventFixture(member).type(CAPITAL_PAYMENT).fiatValue(0.123456)
            .ownershipUnitAmount(0.1).build()
        def event3 = memberCapitalEventFixture(member).type(MEMBERSHIP_BONUS).fiatValue(2000.00)
            .ownershipUnitAmount(1900.0).build()
        def event4 = memberCapitalEventFixture(member).type(MEMBERSHIP_BONUS).fiatValue(0.234567)
            .ownershipUnitAmount(0.2).build()
        def event5 = memberCapitalEventFixture(member).type(UNVESTED_WORK_COMPENSATION).fiatValue(3000.00)
            .ownershipUnitAmount(2900.0).build()
        def event6 = memberCapitalEventFixture(member).type(UNVESTED_WORK_COMPENSATION).fiatValue(0.345678)
            .ownershipUnitAmount(0.3).build()
        def event7 = memberCapitalEventFixture(member).type(WORK_COMPENSATION).fiatValue(4000.00)
            .ownershipUnitAmount(3900.0).build()
        def event8 = memberCapitalEventFixture(member).type(WORK_COMPENSATION).fiatValue(0.456789)
            .ownershipUnitAmount(0.4).build()
        def event9 = memberCapitalEventFixture(member).type(CAPITAL_PAYOUT).fiatValue(-500.00)
          .ownershipUnitAmount(-490.0).build()

        def events = [event1, event2, event3, event4, event5, event6, event7, event8, event9]
        memberCapitalEventRepository.findAllByMemberId(member.id) >> events

        def ownershipUnitPrice = 1.567890
        aggregatedCapitalEventRepository.findTopByOrderByDateDesc() >>
            getAggregatedCapitalEvent(ownershipUnitPrice)

        when:
        CapitalStatement capitalStatement = service.getCapitalStatement(member.id)

        then:
        capitalStatement.capitalPayment == 500.12
        capitalStatement.membershipBonus == 2000.23
        capitalStatement.unvestedWorkCompensation == 3000.35
        capitalStatement.workCompensation == 4000.46
        capitalStatement.profit == 4940.67
        capitalStatement.total == 500.12 + 2000.23 + 3000.35 + 4000.46 + 4940.67
        capitalStatement.currency == EUR
    }

    def "works with no capital"() {
        given:
        Member member = memberFixture().build()
        memberCapitalEventRepository.findAllByMemberId(member.id) >> []
        aggregatedCapitalEventRepository.findTopByOrderByDateDesc() >> null

        when:
        CapitalStatement capitalStatement = service.getCapitalStatement(member.id)

        then:
        capitalStatement.capitalPayment == 0
        capitalStatement.membershipBonus == 0
        capitalStatement.unvestedWorkCompensation == 0
        capitalStatement.workCompensation == 0
        capitalStatement.profit == 0
        capitalStatement.total == 0
        capitalStatement.currency == EUR
    }

    private AggregatedCapitalEvent getAggregatedCapitalEvent(BigDecimal ownershipUnitPrice) {
        new AggregatedCapitalEvent(0,
            INVESTMENT_RETURN,
            new BigDecimal(1),
            new BigDecimal(1),
            new BigDecimal(100),
            ownershipUnitPrice,
            LocalDate.now()
        )
    }
}
