package ee.tuleva.onboarding.auth.authority

import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember

class GrantedAuthorityFactorySpec extends Specification {

    def "from: get member role from authenticated person who is a member"() {
        expect:
        GrantedAuthorityFactory
                .from(sampleAuthenticatedPersonAndMember().build()).stream()
                .findAny().get().authority == Authority.MEMBER
    }

    def "from: get no role from authenticated person who is not member"() {
        expect:
        GrantedAuthorityFactory.from(sampleAuthenticatedPersonNonMember().build()).empty
    }

}
