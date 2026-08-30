package ee.tuleva.onboarding.auth.authority

import ee.tuleva.onboarding.auth.principal.PrincipalUsers
import org.springframework.security.core.authority.SimpleGrantedAuthority
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember
import static java.util.Arrays.asList
import static java.util.Collections.singletonList

class GrantedAuthorityFactorySpec extends Specification {

    def principalUsers = Mock(PrincipalUsers)
    def factory = new GrantedAuthorityFactory(principalUsers)

    def "from: get member role from authenticated person who is a member"() {
        given:
        def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        principalUsers.isMember(authenticatedPerson.userId) >> true

        expect:
        factory.from(authenticatedPerson) == asList(new SimpleGrantedAuthority(Authority.USER),
                new SimpleGrantedAuthority(Authority.MEMBER))
    }

    def "from: get only user role from authenticated person who is not member"() {
        def authenticatedPerson = sampleAuthenticatedPersonNonMember().build()
        principalUsers.isMember(authenticatedPerson.userId) >> false

        expect:
        factory.from(authenticatedPerson) == singletonList(new SimpleGrantedAuthority(Authority.USER))
    }

}
