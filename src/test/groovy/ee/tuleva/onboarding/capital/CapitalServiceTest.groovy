package ee.tuleva.onboarding.capital

import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventRepository
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType
import ee.tuleva.onboarding.capital.event.organisation.OrganisationCapitalEventRepository
import ee.tuleva.onboarding.user.MemberFixture
import ee.tuleva.onboarding.user.member.Member
import spock.lang.Specification

import java.math.RoundingMode

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventFixture.memberCapitalEventFixture

class CapitalServiceTest extends Specification {

    MemberCapitalEventRepository memberCapitalEventRepository = Mock(MemberCapitalEventRepository)
    OrganisationCapitalEventRepository organisationCapitalEventRepository = Mock(OrganisationCapitalEventRepository)
    CapitalService service = new CapitalService(
        memberCapitalEventRepository, organisationCapitalEventRepository
    )

    def "GetCapitalStatement"() {
        given:
        Member member = MemberFixture.memberFixture().build()

        def event1 = memberCapitalEventFixture(member).type(MemberCapitalEventType.CAPITAL_PAYMENT).build()
        def event2 = memberCapitalEventFixture(member).type(MemberCapitalEventType.CAPITAL_PAYMENT).build()
        def event3 = memberCapitalEventFixture(member).type(MemberCapitalEventType.MEMBERSHIP_BONUS).build()
        def event4 = memberCapitalEventFixture(member).type(MemberCapitalEventType.MEMBERSHIP_BONUS).build()

        memberCapitalEventRepository.findAllByMemberId(member.id) >>
            [event1, event2, event3, event4]

        when:
        CapitalStatement capitalStatement = service.getCapitalStatement(member.id)

        then:
        capitalStatement.capitalPayment == event1.fiatValue + event2.fiatValue
        capitalStatement.membershipBonus == event3.fiatValue + event4.fiatValue
        capitalStatement.profit == 0
    }

    def "Calculates ownership unit price"() {
        given:
        Member member = MemberFixture.memberFixture().build()

        def event1 = memberCapitalEventFixture(member).type(MemberCapitalEventType.CAPITAL_PAYMENT).build()
        def event2 = memberCapitalEventFixture(member).type(MemberCapitalEventType.CAPITAL_PAYMENT).build()

        memberCapitalEventRepository.findAllByMemberId(member.id) >>
            [event1, event2]
        def totalValue = new BigDecimal(3300000)
        organisationCapitalEventRepository.getTotalValue() >> totalValue
        def totalOwnershipUnitAmount = new BigDecimal(3100000)
        memberCapitalEventRepository.getTotalOwnershipUnitAmount() >> totalOwnershipUnitAmount
        when:
        BigDecimal price = service.getPricePerOwnershipUnit()

        then:
        price == totalValue.divide(totalOwnershipUnitAmount, 7, RoundingMode.HALF_UP)
    }
}
