package ee.tuleva.onboarding.aml

import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember


class AmlServiceSpec extends Specification {

    AmlCheckRepository amlCheckRepository = Mock()
    AmlService amlService = new AmlService(amlCheckRepository)

    def "adds check"() {
        given:
        def user = sampleUserNonMember().build()
        def type = AmlCheckType.DOCUMENT
        def success = true
        when:
        amlService.addCheck(user, type, success)
        then:
        1 * amlCheckRepository.save({ check ->
            check.user == user &&
                check.type == type &&
                check.success == success
        })
    }

    def "has check"() {
        given:
        def user = sampleUserNonMember().build()
        def type = AmlCheckType.DOCUMENT
        when:
        amlService.hasCheck(user, type)
        then:
        1 * amlCheckRepository.exists({ example ->
            example.getProbe().user == user &&
                example.getProbe().type == type
        })
    }
}
