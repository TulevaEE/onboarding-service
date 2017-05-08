package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.auth.principal.Person;

public class PersonFixture {

    public static Person samplePerson =
        new PersonImp(
                personalCode: "38812121215",
                firstName: "Jordan",
                lastName: "Valdma"
        )

    public static Person samplePerson() {
        return samplePerson
    }

    static class PersonImp implements Person {

        String personalCode

        String firstName

        String lastName

    }

}
