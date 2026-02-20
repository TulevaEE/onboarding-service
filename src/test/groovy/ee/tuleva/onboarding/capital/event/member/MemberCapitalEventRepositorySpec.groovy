package ee.tuleva.onboarding.capital.event.member

import ee.tuleva.onboarding.user.MemberFixture
import ee.tuleva.onboarding.user.member.Member
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventFixture.*

@DataJpaTest
class MemberCapitalEventRepositorySpec extends Specification {

	@Autowired
	private TestEntityManager entityManager

	@Autowired
	private MemberCapitalEventRepository repository

	def "persisting and findAllByUserId() works"() {
		given:
        Member member = generateMember()
        entityManager.persist(member)
        MemberCapitalEvent sampleMemberCapitalEvent =
            memberCapitalEventFixture(member).build()
        entityManager.persist(sampleMemberCapitalEvent)

        Member member2 = generateSecondMember()
        entityManager.persist(member2)
        entityManager.persist(memberCapitalEventFixture(member2).build())

		entityManager.flush()

		when:
		List<MemberCapitalEvent> memberCapitalEvents = repository.findAllByMemberId(member.id)
		List<MemberCapitalEvent> allMemberCapitalEvents = repository.findAll()
        MemberCapitalEvent memberCapitalEvent = memberCapitalEvents.first()

		then:
        allMemberCapitalEvents.size() == 2
        memberCapitalEvents.size() == 1
        memberCapitalEvent.id != null
        memberCapitalEvent.fiatValue == sampleMemberCapitalEvent.fiatValue
        memberCapitalEvent.member == sampleMemberCapitalEvent.member
        memberCapitalEvent.ownershipUnitAmount == sampleMemberCapitalEvent.ownershipUnitAmount
        memberCapitalEvent.type == sampleMemberCapitalEvent.type
	}

    def "calculates total ownership unit amounts"() {
        given:
        Member member = generateMember()
        entityManager.persist(member)
        MemberCapitalEvent sampleMemberCapitalEvent =
            memberCapitalEventFixture(member).build()
        entityManager.persist(sampleMemberCapitalEvent)

        Member member2 = generateSecondMember()
        entityManager.persist(member2)
        MemberCapitalEvent sampleMemberCapitalEvent2 = memberCapitalEventFixture(member2).build()
        entityManager.persist(sampleMemberCapitalEvent2)

        entityManager.flush()

        when:
        BigDecimal amount = repository.getTotalOwnershipUnitAmount()

        then:
        Math.round(amount) ==
            Math.round(sampleMemberCapitalEvent.ownershipUnitAmount + sampleMemberCapitalEvent2.ownershipUnitAmount)
    }

    private Member generateSecondMember() {
        MemberFixture.memberFixture().id(null).user(
            entityManager.persist(sampleUserNonMember().id(null)
                .personalCode("37605030299")
                .email("jordan@valdma.com")
                .build())
        ).memberNumber(124).build()
    }

    private Member generateMember() {
        MemberFixture.memberFixture().id(null).user(
            entityManager.persist(sampleUserNonMember().id(null).build())
        ).build()
    }
}
