package ee.tuleva.onboarding.user.personalcode

import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate

class PersonalCodeSpec extends Specification {

    @Unroll
    def "can get date of birth with different centuries: #personalCode"() {
        expect:
        PersonalCode.getDateOfBirth(personalCode) == dateOfBirth

        where:
        personalCode  | dateOfBirth
        "38501020000" | LocalDate.of(1985, 01, 02)
        "48501020000" | LocalDate.of(1985, 01, 02)
        "50301020000" | LocalDate.of(2003, 01, 02)
        "60301020000" | LocalDate.of(2003, 01, 02)
    }

}
