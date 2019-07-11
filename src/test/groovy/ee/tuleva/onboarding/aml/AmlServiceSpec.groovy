package ee.tuleva.onboarding.aml


import ee.tuleva.onboarding.audit.AuditEventPublisher
import ee.tuleva.onboarding.audit.AuditEventType
import ee.tuleva.onboarding.auth.UserFixture
import ee.tuleva.onboarding.epis.contact.UserPreferences
import ee.tuleva.onboarding.mandate.MandateFixture
import spock.lang.Specification

import static ee.tuleva.onboarding.aml.AmlCheckType.*
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember

class AmlServiceSpec extends Specification {

    AmlCheckRepository amlCheckRepository = Mock()
    AuditEventPublisher auditEventPublisher = Mock()
    AmlService amlService = new AmlService(amlCheckRepository, auditEventPublisher)

    def "adds user checks"() {
        given:
        def user = sampleUserNonMember().build()
        when:
        amlService.checkUserAfterLogin(user, user)
        then:
        1 * amlCheckRepository.save({ check ->
            check.user == user &&
                check.type == DOCUMENT &&
                check.success
        })
        1 * amlCheckRepository.save({ check ->
            check.user == user &&
                check.type == SK_NAME &&
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
                check.type == PENSION_REGISTRY_NAME &&
                check.success
        })
        1 * amlCheckRepository.existsByUserAndType(user, PENSION_REGISTRY_NAME) >> false
    }

    def "adds check if missing"() {
        given:
        def user = sampleUserNonMember().build()
        def type = DOCUMENT
        def success = true
        def amlCheck = AmlCheck.builder().user(user).type(type).success(success).build()
        when:
        amlService.addCheckIfMissing(amlCheck)
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
        def type = DOCUMENT
        def success = true
        def amlCheck = AmlCheck.builder().user(user).type(type).success(success).build()
        when:
        amlService.addCheckIfMissing(amlCheck)
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

    def "does not do checks for second pillar"() {
        given:
        def mandate = MandateFixture.sampleMandate()
        mandate.pillar = 2
        when:
        def result = amlService.allChecksPassed(mandate)
        then:
        result
    }

    def "sees if all checks are passed for third pillar"(List<AmlCheck> checks, boolean result) {
        given:
        def mandate = MandateFixture.sampleMandate()
        mandate.user = UserFixture.sampleUser().build()
        mandate.pillar = 3
        when:
        def actual = amlService.allChecksPassed(mandate)
        then:
        actual == result
        1 * amlCheckRepository.findAllByUser(mandate.user) >> checks
        if (!result) {
            1 * auditEventPublisher.publish(mandate.user.getEmail(), AuditEventType.MANDATE_DENIED)
        }
        where:
        checks                                                                                                      | result
        []                                                                                                          | false
        successfulChecks(POLITICALLY_EXPOSED_PERSON, RESIDENCY_AUTO, DOCUMENT, PENSION_REGISTRY_NAME, OCCUPATION)   | true
        successfulChecks(POLITICALLY_EXPOSED_PERSON, RESIDENCY_MANUAL, DOCUMENT, PENSION_REGISTRY_NAME, OCCUPATION) | true
        successfulChecks(POLITICALLY_EXPOSED_PERSON, RESIDENCY_AUTO, DOCUMENT, SK_NAME, OCCUPATION)                 | true
        successfulChecks(POLITICALLY_EXPOSED_PERSON, RESIDENCY_MANUAL, DOCUMENT, SK_NAME, OCCUPATION)               | true
        [check(POLITICALLY_EXPOSED_PERSON, false)] +
            successfulChecks(RESIDENCY_MANUAL, DOCUMENT, SK_NAME, PENSION_REGISTRY_NAME, OCCUPATION)                | false
    }

    private static List<AmlCheck> successfulChecks(AmlCheckType... checkTypes) {
        return checkTypes.collect({ type -> check(type) })
    }

    private static AmlCheck check(AmlCheckType type, boolean success = true) {
        return AmlCheck.builder().type(type).success(success).build()
    }
}
