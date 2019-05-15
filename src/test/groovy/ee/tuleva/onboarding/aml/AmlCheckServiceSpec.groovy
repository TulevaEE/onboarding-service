package ee.tuleva.onboarding.aml

import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification

import static ee.tuleva.onboarding.aml.AmlCheckType.*
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class AmlCheckServiceSpec extends Specification {

    AmlService amlService = Mock()
    UserService userService = Mock()
    AmlCheckService amlCheckService = new AmlCheckService(amlService, userService)

    private static AmlCheck check(AmlCheckType type) {
        return AmlCheck.builder().user(sampleUser().build()).type(type).build()
    }


    def "returns only missing checks"(List<AmlCheck> checks, List<AmlCheckType> missing) {
        given:
        def user = sampleUser().build()
        when:
        def result = amlCheckService.getMissingChecks(user.id)
        then:
        result == missing
        1 * userService.getById(user.id) >> user
        1 * amlService.getChecks(user) >> checks
        where:
        checks                                                       | missing
        []                                                           | [RESIDENCY_MANUAL, POLITICALLY_EXPOSED_PERSON]
        [check(DOCUMENT)]                                            | [RESIDENCY_MANUAL, POLITICALLY_EXPOSED_PERSON]
        [check(RESIDENCY_AUTO)]                                      | [POLITICALLY_EXPOSED_PERSON]
        [check(RESIDENCY_MANUAL)]                                    | [POLITICALLY_EXPOSED_PERSON]
        [check(POLITICALLY_EXPOSED_PERSON)]                          | [RESIDENCY_MANUAL]
        [check(POLITICALLY_EXPOSED_PERSON), check(RESIDENCY_MANUAL)] | []
        [check(POLITICALLY_EXPOSED_PERSON), check(RESIDENCY_AUTO)]   | []
    }

    def "adds check if missing"() {
        given:
        def user = sampleUser().build()
        def type = DOCUMENT
        def success = true
        when:
        amlCheckService.addCheckIfMissing(user.id, type, success)
        then:
        1 * amlService.addCheckIfMissing(user, type, success)
        1 * userService.getById(user.id) >> user
    }
}
