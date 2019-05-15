package ee.tuleva.onboarding.aml


import ee.tuleva.onboarding.epis.contact.UserPreferences
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember

class AmlServiceSpec extends Specification {

    AmlCheckRepository amlCheckRepository = Mock()
    AmlService amlService = new AmlService(amlCheckRepository)

    def "adds user checks"() {
        given:
        def user = sampleUserNonMember().build()
        when:
        amlService.checkUserAfterLogin(user, user)
        then:
        1 * amlCheckRepository.save({ check ->
            check.user == user &&
                check.type == AmlCheckType.DOCUMENT &&
                check.success
        })
        1 * amlCheckRepository.save({ check ->
            check.user == user &&
                check.type == AmlCheckType.SK_NAME &&
                check.success
        })
    }

    def "add pension registry name check if missing"() {
        given:
        def user = sampleUserNonMember().build()
        def userPreferences = UserPreferences.builder()
            .firstName(user.firstName)
            .lastName(user.lastName)
            .personalCode(user.personalCode)
            .build()
        when:
        amlService.addPensionRegistryNameCheckIfMissing(user, userPreferences)
        then:
        1 * amlCheckRepository.save({ check ->
            check.user == user &&
                check.type == AmlCheckType.PENSION_REGISTRY_NAME &&
                check.success
        })
        1 * amlCheckRepository.existsByUserAndType(user, AmlCheckType.PENSION_REGISTRY_NAME) >> false
    }

    def "adds check if missing"() {
        given:
        def user = sampleUserNonMember().build()
        def type = AmlCheckType.DOCUMENT
        def success = true
        when:
        amlService.addCheckIfMissing(user, type, success)
        then:
        1 * amlCheckRepository.save({ check ->
            check.user == user &&
                check.type == type &&
                check.success == success
        })
    }

    def "does not add check if not missing"() {
        given:
        def user = sampleUserNonMember().build()
        def type = AmlCheckType.DOCUMENT
        def success = true
        when:
        amlService.addCheckIfMissing(user, type, success)
        then:
        0 * amlCheckRepository.save(_)
        1 * amlCheckRepository.existsByUserAndType(user, type) >> true
    }

    def "gives all checks"() {
        given:
        def user = sampleUserNonMember().build()
        when:
        amlService.getChecks(user)
        then:
        1 * amlCheckRepository.findAllByUser(user)
    }
}
