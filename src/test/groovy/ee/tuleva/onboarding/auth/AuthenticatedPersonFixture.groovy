package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.role.Role
import ee.tuleva.onboarding.auth.role.RoleType
import ee.tuleva.onboarding.user.User

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON

class AuthenticatedPersonFixture {

    static AuthenticatedPerson.AuthenticatedPersonBuilder sampleAuthenticatedPersonAndMember() {
        return authenticatedPersonFromUser(sampleUser().build())
    }

    static AuthenticatedPerson.AuthenticatedPersonBuilder sampleAuthenticatedPersonNonMember() {
        return authenticatedPersonFromUser(sampleUserNonMember().build())
    }

    static AuthenticatedPerson.AuthenticatedPersonBuilder sampleAuthenticatedPersonLegalEntity() {
        return authenticatedPersonFromUser(sampleUser().build())
                .role(new Role(LEGAL_ENTITY, "12345678", "Acme OÜ"))
    }

    static AuthenticatedPerson.AuthenticatedPersonBuilder authenticatedPersonFromUser(User user) {
        return AuthenticatedPerson.builder()
                .firstName(user.firstName)
                .lastName(user.lastName)
                .personalCode(user.personalCode)
                .userId(user.id)
                .role(new Role(PERSON, user.personalCode, user.fullName))
    }

}
