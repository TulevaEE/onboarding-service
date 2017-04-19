package ee.tuleva.onboarding.auth.authority

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture
import spock.lang.Specification

class GrantedAuthorityFactorySpec extends Specification {

    def "from: get member role from authenticated person who is a member"() {
        expect:
        GrantedAuthorityFactory
                .from(AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember).stream()
                .findAny().get().authority == Authority.MEMBER
    }

    def "from: get no role from authenticated person who is not member"() {
        expect:
        GrantedAuthorityFactory.from(AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember).empty
    }

}
