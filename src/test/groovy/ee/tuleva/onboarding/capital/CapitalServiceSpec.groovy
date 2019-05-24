package ee.tuleva.onboarding.capital

import ee.tuleva.onboarding.capital.event.AggregatedCapitalEvent
import ee.tuleva.onboarding.capital.event.AggregatedCapitalEventRepository
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventRepository
import ee.tuleva.onboarding.capital.event.organisation.OrganisationCapitalEventType
import ee.tuleva.onboarding.user.MemberFixture
import ee.tuleva.onboarding.user.member.Member
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventFixture.memberCapitalEventFixture
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.*

class CapitalServiceSpec extends Specification {
    MemberCapitalEventRepository memberCapitalEventRepository = Mock(MemberCapitalEventRepository)
    AggregatedCapitalEventRepository aggregatedCapitalEventRepository = Mock(AggregatedCapitalEventRepository)
    CapitalService service = new CapitalService(memberCapitalEventRepository, aggregatedCapitalEventRepository)

    def "GetCapitalStatement"() {
        given:
        Member member = MemberFixture.memberFixture().build()
        def event1 = memberCapitalEventFixture(member).type(CAPITAL_PAYMENT).fiatValue(1000.00)
            .ownershipUnitAmount(1.0).build()
        def event2 = memberCapitalEventFixture(member).type(CAPITAL_PAYMENT).fiatValue(0.123456)
            .ownershipUnitAmount(2.0).build()
        def event3 = memberCapitalEventFixture(member).type(MEMBERSHIP_BONUS).fiatValue(2000.00)
            .ownershipUnitAmount(3.0).build()
        def event4 = memberCapitalEventFixture(member).type(MEMBERSHIP_BONUS).fiatValue(0.234567)
            .ownershipUnitAmount(4.0).build()
        def event5 = memberCapitalEventFixture(member).type(UNVESTED_WORK_COMPENSATION).fiatValue(3000.00)
            .ownershipUnitAmount(5.0).build()
        def event6 = memberCapitalEventFixture(member).type(UNVESTED_WORK_COMPENSATION).fiatValue(0.345678)
            .ownershipUnitAmount(6.0).build()
        def event7 = memberCapitalEventFixture(member).type(WORK_COMPENSATION).fiatValue(4000.00)
            .ownershipUnitAmount(7.0).build()
        def event8 = memberCapitalEventFixture(member).type(WORK_COMPENSATION).fiatValue(0.456789)
            .ownershipUnitAmount(8.0).build()

        def events = [event1, event2, event3, event4, event5, event6, event7, event8]
        memberCapitalEventRepository.findAllByMemberId(member.id) >> events

        def ownershipUnitPrice = 5000.567890
        aggregatedCapitalEventRepository.findTopByOrderByDateDesc() >>
            getAggregatedCapitalEvent(ownershipUnitPrice)

        when:
        CapitalStatement capitalStatement = service.getCapitalStatement(member.id)

        then:
        capitalStatement.capitalPayment == 1000.12
        capitalStatement.membershipBonus == 2000.23
        capitalStatement.unvestedWorkCompensation == 3000.35
        capitalStatement.workCompensation == 4000.46
        capitalStatement.profit == 170_019.28
    }


    private AggregatedCapitalEvent getAggregatedCapitalEvent(BigDecimal ownershipUnitPrice) {
        new AggregatedCapitalEvent(0,
            OrganisationCapitalEventType.FIAT_RETURN,
            new BigDecimal(1),
            new BigDecimal(1),
            new BigDecimal(100),
            ownershipUnitPrice,
            LocalDate.now()
        )
    }
}
