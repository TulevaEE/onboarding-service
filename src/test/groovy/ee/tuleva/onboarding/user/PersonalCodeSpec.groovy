package ee.tuleva.onboarding.user

import spock.lang.Specification

import java.time.LocalDate

class PersonalCodeSpec extends Specification {

    def "getBirthDate works with different centuries"() {
        expect:
        PersonalCode.getBirthDate(personalCode) == birthDate

        where:
        personalCode  | birthDate
        "38501020000" | LocalDate.of(1985, 01, 02)
        "48501020000" | LocalDate.of(1985, 01, 02)
        "50301020000" | LocalDate.of(2003, 01, 02)
        "60301020000" | LocalDate.of(2003, 01, 02)
    }

}
