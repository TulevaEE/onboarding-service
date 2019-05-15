package ee.tuleva.onboarding.aml

import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.contact.UserPreferences
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember

class AmlServiceSpec extends Specification {

    AmlCheckRepository amlCheckRepository = Mock()
    EpisService episService = Mock()
    AmlService amlService = new AmlService(amlCheckRepository, episService)

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
                check.type == AmlCheckType.PENSION_REGISTRY_NAME &&
                check.success
        })
        1 * amlCheckRepository.save({ check ->
            check.user == user &&
                check.type == AmlCheckType.SK_NAME &&
                check.success
        })
        1 * episService.getContactDetails(_) >> UserPreferences.builder()
            .firstName(user.firstName)
            .lastName(user.lastName)
            .personalCode(user.personalCode)
            .build()
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
}
