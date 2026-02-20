package ee.tuleva.onboarding.capital.event

import ee.tuleva.onboarding.capital.event.member.MemberCapitalEvent
import ee.tuleva.onboarding.capital.event.organisation.OrganisationCapitalEvent
import ee.tuleva.onboarding.capital.event.organisation.OrganisationCapitalEventFixture
import ee.tuleva.onboarding.user.MemberFixture
import ee.tuleva.onboarding.user.member.Member
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import spock.lang.Specification

import java.math.MathContext
import java.time.LocalDate

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventFixture.memberCapitalEventFixture

@DataJpaTest
class AggregatedCapitalEventRepositorySpec extends Specification {

	@Autowired
	private TestEntityManager entityManager

	@Autowired
	private AggregatedCapitalEventRepository repository

	def "aggregates correctly"() {
		given:
        Member member = generateMember()
        entityManager.persist(member)
        MemberCapitalEvent memberCapitalEvent1 =
            memberCapitalEventFixture(member)
                .accountingDate(LocalDate.parse("2017-12-31"))
                .effectiveDate(LocalDate.parse("2017-12-31"))
                .build()
        entityManager.persist(memberCapitalEvent1)

        MemberCapitalEvent memberCapitalEvent2 =
            memberCapitalEventFixture(member)
                .accountingDate(LocalDate.parse("2017-12-31"))
                .effectiveDate(LocalDate.parse("2017-12-31"))
                .build()
        entityManager.persist(memberCapitalEvent2)

        Member member2 = generateSecondMember()
        entityManager.persist(member2)

        MemberCapitalEvent memberCapitalEventNextYear1 =
            memberCapitalEventFixture(member)
                .accountingDate(LocalDate.parse("2018-12-31"))
                .effectiveDate(LocalDate.parse("2018-12-31"))
                .build()
        entityManager.persist(memberCapitalEventNextYear1)

        MemberCapitalEvent memberCapitalEventNextYear2 =
            memberCapitalEventFixture(member)
                .accountingDate(LocalDate.parse("2018-12-31"))
                .effectiveDate(LocalDate.parse("2018-12-31"))
                .build()
        entityManager.persist(memberCapitalEventNextYear2)

        OrganisationCapitalEvent organisationCapitalEvent = OrganisationCapitalEventFixture.fixture()
            .date(LocalDate.parse("2017-12-31"))
            .build()
        entityManager.persist(organisationCapitalEvent)
        OrganisationCapitalEvent organisationCapitalEventNextYear = OrganisationCapitalEventFixture.fixture()
            .date(LocalDate.parse("2018-12-31"))
            .build()
        entityManager.persist(organisationCapitalEventNextYear)


		entityManager.flush()

		when:
        def events = repository.findAll()

		then:
        events.size() == 2
        assertBigDecimals(events[0].totalFiatValue,
            organisationCapitalEvent.fiatValue)
        assertBigDecimals(events[0].totalOwnershipUnitAmount,
            memberCapitalEvent1.ownershipUnitAmount + memberCapitalEvent2.ownershipUnitAmount)
        assertBigDecimals(events[1].totalFiatValue,
            organisationCapitalEvent.fiatValue + organisationCapitalEventNextYear.fiatValue)
        assertBigDecimals(events[1].totalOwnershipUnitAmount,
            memberCapitalEvent1.ownershipUnitAmount + memberCapitalEvent2.ownershipUnitAmount +
            memberCapitalEventNextYear1.ownershipUnitAmount + memberCapitalEventNextYear2.ownershipUnitAmount
        )
	}

    def "finds latest event"() {
        given:
        OrganisationCapitalEvent organisationCapitalEvent = OrganisationCapitalEventFixture.fixture()
            .date(LocalDate.parse("2017-12-31"))
            .build()
        entityManager.persist(organisationCapitalEvent)
        OrganisationCapitalEvent organisationCapitalEventNextYear = OrganisationCapitalEventFixture.fixture()
            .date(LocalDate.parse("2018-12-31"))
            .build()
        entityManager.persist(organisationCapitalEventNextYear)


        entityManager.flush()

        when:
        def event = repository.findTopByOrderByDateDesc()

        then:
        event.date == organisationCapitalEventNextYear.date
    }


    private assertBigDecimals(BigDecimal expected, BigDecimal actual) {
        expected.round(new MathContext(5)) == actual.round(new MathContext(5))
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
