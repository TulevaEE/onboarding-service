package ee.tuleva.onboarding.auth.authority

import ee.tuleva.onboarding.auth.principal.PrincipalUsers
import ee.tuleva.onboarding.auth.role.ChildRepresentations
import ee.tuleva.onboarding.auth.role.Role
import org.springframework.security.core.authority.SimpleGrantedAuthority
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember
import static ee.tuleva.onboarding.auth.authority.Authority.MEMBER
import static ee.tuleva.onboarding.auth.authority.Authority.USER
import static ee.tuleva.onboarding.auth.authority.Authority.WARD
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON

class GrantedAuthorityFactorySpec extends Specification {

    static final String WARD_PERSONAL_CODE = "38001085718"

    def principalUsers = Mock(PrincipalUsers)
    def childRepresentations = Mock(ChildRepresentations)
    def factory = new GrantedAuthorityFactory(principalUsers, childRepresentations)

    def "from: get member role from authenticated person who is a member"() {
        given:
        def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        principalUsers.isMember(authenticatedPerson.userId) >> true
        childRepresentations.hasRestrictedLegalCapacity(authenticatedPerson.personalCode) >> false

        expect:
        factory.from(authenticatedPerson) == [new SimpleGrantedAuthority(USER),
                                              new SimpleGrantedAuthority(MEMBER)]
    }

    def "from: get only user role from authenticated person who is not member"() {
        given:
        def authenticatedPerson = sampleAuthenticatedPersonNonMember().build()
        principalUsers.isMember(authenticatedPerson.userId) >> false
        childRepresentations.hasRestrictedLegalCapacity(authenticatedPerson.personalCode) >> false

        expect:
        factory.from(authenticatedPerson) == [new SimpleGrantedAuthority(USER)]
    }

    def "from: a person with restricted legal capacity acting as self is also a ward"() {
        given:
        def ward = sampleAuthenticatedPersonNonMember().build()
        principalUsers.isMember(ward.userId) >> false
        childRepresentations.hasRestrictedLegalCapacity(ward.personalCode) >> true

        expect:
        factory.from(ward) == [new SimpleGrantedAuthority(USER),
                               new SimpleGrantedAuthority(WARD)]
    }

    def "from: a ward who is a member keeps membership"() {
        given:
        def ward = sampleAuthenticatedPersonAndMember().build()
        principalUsers.isMember(ward.userId) >> true
        childRepresentations.hasRestrictedLegalCapacity(ward.personalCode) >> true

        expect:
        factory.from(ward) == [new SimpleGrantedAuthority(USER),
                               new SimpleGrantedAuthority(WARD),
                               new SimpleGrantedAuthority(MEMBER)]
    }

    def "from: a guardian acting in the ward's role stays a user"() {
        given:
        def guardian = sampleAuthenticatedPersonNonMember()
                .role(new Role(PERSON, WARD_PERSONAL_CODE, "Ward Name"))
                .build()
        principalUsers.isMember(guardian.userId) >> false

        when:
        def authorities = factory.from(guardian)

        then:
        0 * childRepresentations.hasRestrictedLegalCapacity(_)
        authorities == [new SimpleGrantedAuthority(USER)]
    }
}
