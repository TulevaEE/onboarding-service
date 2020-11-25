package ee.tuleva.onboarding.aml

import ee.tuleva.onboarding.aml.dto.AmlCheckAddCommand
import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.aml.AmlCheckType.*
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class AmlCheckServiceSpec extends Specification {

    AmlService amlService = Mock()
    UserService userService = Mock()
    AmlCheckService amlCheckService = new AmlCheckService(amlService, userService)

    private static AmlCheck check(AmlCheckType type) {
        return AmlCheck.builder().user(sampleUser().build()).type(type).build()
    }


    @Unroll
    def "returns only missing checks"() {
        given:
        def user = sampleUser().build()
        when:
        def result = amlCheckService.getMissingChecks(user.id)
        then:
        result == missing
        1 * userService.getById(user.id) >> user
        1 * amlService.getChecks(user) >> checks
        where:
        checks                              | missing
        []                                  | [RESIDENCY_MANUAL, POLITICALLY_EXPOSED_PERSON, OCCUPATION, CONTACT_DETAILS]
        [check(DOCUMENT)]                   | [RESIDENCY_MANUAL, POLITICALLY_EXPOSED_PERSON, OCCUPATION, CONTACT_DETAILS]
        [check(RESIDENCY_AUTO)]             | [POLITICALLY_EXPOSED_PERSON, OCCUPATION, CONTACT_DETAILS]
        [check(RESIDENCY_MANUAL)]           | [POLITICALLY_EXPOSED_PERSON, OCCUPATION, CONTACT_DETAILS]
        [check(POLITICALLY_EXPOSED_PERSON)] | [RESIDENCY_MANUAL, OCCUPATION, CONTACT_DETAILS]
        [check(OCCUPATION)]                 | [RESIDENCY_MANUAL, POLITICALLY_EXPOSED_PERSON, CONTACT_DETAILS]
        [check(CONTACT_DETAILS)]            | [RESIDENCY_MANUAL, POLITICALLY_EXPOSED_PERSON, OCCUPATION]
        [
            check(POLITICALLY_EXPOSED_PERSON),
            check(RESIDENCY_MANUAL),
            check(OCCUPATION),
            check(CONTACT_DETAILS)
        ]                                   | []
        [
            check(POLITICALLY_EXPOSED_PERSON),
            check(RESIDENCY_AUTO),
            check(OCCUPATION),
            check(CONTACT_DETAILS)
        ]                                   | []
    }

    def "adds check if missing"() {
        given:
        def user = sampleUser().build()
        def command = AmlCheckAddCommand.builder().type(DOCUMENT).success(true).build()
        def amlCheck = AmlCheck.builder().user(user).type(DOCUMENT).success(true).build()
        when:
        amlCheckService.addCheckIfMissing(user.id, command)
        then:
        1 * amlService.addCheckIfMissing(amlCheck)
        1 * userService.getById(user.id) >> user
    }
}
