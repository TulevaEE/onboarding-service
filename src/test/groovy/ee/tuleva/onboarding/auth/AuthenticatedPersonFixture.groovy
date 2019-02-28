package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.user.User

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember

class AuthenticatedPersonFixture {

    static AuthenticatedPerson.AuthenticatedPersonBuilder sampleAuthenticatedPersonAndMember() {
        return authenticatedPersonFromUser(sampleUser().build())
    }

    static AuthenticatedPerson.AuthenticatedPersonBuilder sampleAuthenticatedPersonNonMember() {
        return authenticatedPersonFromUser(sampleUserNonMember().build())
    }

    static AuthenticatedPerson.AuthenticatedPersonBuilder authenticatedPersonFromUser(User user) {
        return AuthenticatedPerson.builder()
                .firstName(user.firstName)
                .lastName(user.lastName)
                .personalCode(user.personalCode)
                .userId(user.id)
    }

}
