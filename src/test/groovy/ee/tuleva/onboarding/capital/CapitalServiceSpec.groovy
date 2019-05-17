package ee.tuleva.onboarding.capital

import ee.tuleva.onboarding.capital.event.AggregatedCapitalEvent
import ee.tuleva.onboarding.capital.event.AggregatedCapitalEventRepository
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventRepository
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType
import ee.tuleva.onboarding.capital.event.organisation.OrganisationCapitalEventRepository
import ee.tuleva.onboarding.capital.event.organisation.OrganisationCapitalEventType
import ee.tuleva.onboarding.user.MemberFixture
import ee.tuleva.onboarding.user.member.Member
import spock.lang.Specification

import java.math.MathContext
import java.time.LocalDate

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventFixture.memberCapitalEventFixture

class CapitalServiceSpec extends Specification {
    MemberCapitalEventRepository memberCapitalEventRepository = Mock(MemberCapitalEventRepository)
    OrganisationCapitalEventRepository organisationCapitalEventRepository = Mock(OrganisationCapitalEventRepository)
    AggregatedCapitalEventRepository aggregatedCapitalEventRepository = Mock(AggregatedCapitalEventRepository)
    CapitalService service = new CapitalService(
        memberCapitalEventRepository, organisationCapitalEventRepository,
        aggregatedCapitalEventRepository
    )

    def "GetCapitalStatement"() {
        given:
        Member member = MemberFixture.memberFixture().build()

        def event1 = memberCapitalEventFixture(member).type(MemberCapitalEventType.CAPITAL_PAYMENT).build()
        def event2 = memberCapitalEventFixture(member).type(MemberCapitalEventType.CAPITAL_PAYMENT).build()
        def event3 = memberCapitalEventFixture(member).type(MemberCapitalEventType.MEMBERSHIP_BONUS).build()
        def event4 = memberCapitalEventFixture(member).type(MemberCapitalEventType.MEMBERSHIP_BONUS).build()
        def event5 = memberCapitalEventFixture(member).type(MemberCapitalEventType.UNVESTED_WORK_COMPENSATION).build()
        def event6 = memberCapitalEventFixture(member).type(MemberCapitalEventType.UNVESTED_WORK_COMPENSATION).build()
        def event7 = memberCapitalEventFixture(member).type(MemberCapitalEventType.WORK_COMPENSATION).build()
        def event8 = memberCapitalEventFixture(member).type(MemberCapitalEventType.WORK_COMPENSATION).build()

        def events = [event1, event2, event3, event4, event5, event6, event7, event8]
        memberCapitalEventRepository.findAllByMemberId(member.id) >> events

        def ownershipUnitPrice = new BigDecimal((new Random()).nextDouble() * 1000)
        aggregatedCapitalEventRepository.findTopByOrderByDateDesc() >>
            getAggregatedCapitalEvent(ownershipUnitPrice)

        when:
        CapitalStatement capitalStatement = service.getCapitalStatement(member.id)

        then:
        assertBigDecimals(capitalStatement.capitalPayment, event1.fiatValue + event2.fiatValue)
        assertBigDecimals(capitalStatement.membershipBonus, event3.fiatValue + event4.fiatValue)
        assertBigDecimals(capitalStatement.unvestedWorkCompensation, event5.fiatValue + event6.fiatValue)
        assertBigDecimals(capitalStatement.workCompensation, event7.fiatValue + event8.fiatValue)
        assertBigDecimals(capitalStatement.profit,
            (events.sum { it.ownershipUnitAmount *  ownershipUnitPrice}) - events.sum { it.fiatValue })
    }

    private assertBigDecimals(BigDecimal expected, BigDecimal actual) {
        expected.round(new MathContext(2)) == actual.round(new MathContext(2))
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
