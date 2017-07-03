package ee.tuleva.onboarding.auth.authority

import ee.tuleva.onboarding.user.UserService
import org.springframework.security.core.authority.SimpleGrantedAuthority
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static java.util.Arrays.asList
import static java.util.Collections.singletonList

class GrantedAuthorityFactorySpec extends Specification {

    def userService = Mock(UserService)
    def factory = new GrantedAuthorityFactory(userService)

    def "from: get member role from authenticated person who is a member"() {
        given:
        def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        userService.getById(authenticatedPerson.userId) >> sampleUser().build()

        expect:
        factory.from(authenticatedPerson) == asList(new SimpleGrantedAuthority(Authority.USER),
                new SimpleGrantedAuthority(Authority.MEMBER))
    }

    def "from: get only user role from authenticated person who is not member"() {
        def authenticatedPerson = sampleAuthenticatedPersonNonMember().build()
        userService.getById(authenticatedPerson.userId) >> sampleUserNonMember().build()

        expect:
        factory.from(authenticatedPerson) == singletonList(new SimpleGrantedAuthority(Authority.USER))
    }

}
