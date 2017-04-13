package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.auth.principal.Person;

public class PersonFixture {

    public static Person samplePerson() {
        return new PersonImp(
                personalCode: "123",
                firstName: "First",
                lastName: "Last"
        )
    }

    static class PersonImp implements Person {

        String personalCode

        String firstName

        String lastName

    }

}
