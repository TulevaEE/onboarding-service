package ee.tuleva.onboarding.aml

import ee.tuleva.onboarding.audit.AuditEventPublisher
import ee.tuleva.onboarding.audit.AuditEventType
import ee.tuleva.onboarding.epis.contact.UserPreferences
import ee.tuleva.onboarding.user.User
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Clock
import java.time.Instant

import static ee.tuleva.onboarding.aml.AmlCheckType.*
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static java.time.ZoneOffset.UTC
import static java.time.temporal.ChronoUnit.DAYS

class AmlServiceSpec extends Specification {

    AmlCheckRepository amlCheckRepository = Mock()
    AuditEventPublisher auditEventPublisher = Mock()
    Clock clock = Clock.fixed(Instant.parse("2020-11-23T10:00:00Z"), UTC)
    AmlService amlService = new AmlService(amlCheckRepository, auditEventPublisher, clock)

    def aYearAgo = Instant.now(clock).minus(365, DAYS)

    def "adds aml checks after login"() {
        given:
        def user = sampleUserNonMember().build()
        def isResident = true
        when:
        amlService.checkUserBeforeLogin(user, user, isResident)
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
        1 * amlCheckRepository.save({ check ->
            check.user == user &&
                check.type == RESIDENCY_AUTO &&
                check.success
        })
    }

    def "does not add residency check if its null"() {
        given:
        def user = sampleUserNonMember().build()
        def isResident = null
        when:
        amlService.checkUserBeforeLogin(user, user, isResident)
        then:
        2 * amlCheckRepository.save({ check ->
            check.user == user &&
                check.type != RESIDENCY_AUTO &&
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
        1 * amlCheckRepository.existsByUserAndTypeAndCreatedTimeAfter(user, PENSION_REGISTRY_NAME, aYearAgo) >> false
        1 * amlCheckRepository.save({ check ->
            check.user == user &&
                check.type == PENSION_REGISTRY_NAME &&
                check.success
        })
    }

    def "add contact details check if missing"() {
        given:
        def user = sampleUserNonMember().build()
        when:
        amlService.addContactDetailsCheckIfMissing(user)
        then:
        1 * amlCheckRepository.existsByUserAndTypeAndCreatedTimeAfter(user, CONTACT_DETAILS, aYearAgo) >> false
        1 * amlCheckRepository.save({ check ->
            check.user == user &&
                check.type == CONTACT_DETAILS &&
                check.success
        })
    }

    def "adds check if missing"() {
        given:
        def user = sampleUserNonMember().build()
        def type = DOCUMENT
        def success = true
        def amlCheck = check(type, success, user)
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
        def amlCheck = check(type, success, user)
        when:
        amlService.addCheckIfMissing(amlCheck)
        then:
        1 * amlCheckRepository.existsByUserAndTypeAndCreatedTimeAfter(user, type, aYearAgo) >> true
        0 * amlCheckRepository.save(_)
    }

    def "gives all checks"() {
        given:
        def user = sampleUserNonMember().build()
        when:
        amlService.getChecks(user)
        then:
        1 * amlCheckRepository.findAllByUserAndCreatedTimeAfter(user, aYearAgo)
    }

    def "does not do checks for second pillar"() {
        given:
        def user = sampleUser().build()
        def pillar = 2
        when:
        def result = amlService.allChecksPassed(user, pillar)
        then:
        result
    }

    @Unroll
    def "sees if all checks are passed for third pillar"() {
        given:
        def user = sampleUser().build()
        def pillar = 3
        when:
        def actual = amlService.allChecksPassed(user, pillar)
        then:
        actual == result
        1 * amlCheckRepository.findAllByUserAndCreatedTimeAfter(user, aYearAgo) >> checks
        if (!result) {
            1 * auditEventPublisher.publish(user.getEmail(), AuditEventType.MANDATE_DENIED)
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

    private static AmlCheck check(AmlCheckType type, boolean success = true, User user = null) {
        return AmlCheck.builder().type(type).success(success).user(user).build()
    }
}
