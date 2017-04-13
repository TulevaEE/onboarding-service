package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;

public class AuthenticatedPersonFixture {

    public static AuthenticatedPerson sampleAuthenticatedPerson =
            AuthenticatedPerson.builder()
                .firstName(UserFixture.sampleUser().getFirstName())
                .lastName(UserFixture.sampleUser().getLastName())
                .personalCode(UserFixture.sampleUser().getLastName())
                .user(UserFixture.sampleUser())
                .build()

}
