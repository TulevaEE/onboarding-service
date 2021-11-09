package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.auth.principal.Person
import groovy.transform.ToString

class PersonFixture {

    public static Person samplePerson =
        new PersonImpl(
            personalCode: "38812121215",
            firstName: "Jordan",
            lastName: "Valdma"
        )

    static Person samplePerson() {
        return samplePerson
    }

    @ToString
    static class PersonImpl implements Person {

        String personalCode

        String firstName

        String lastName

        String phoneNumber

    }

}
