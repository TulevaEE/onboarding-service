package ee.tuleva.onboarding.aml

import ee.tuleva.onboarding.aml.dto.AmlCheckAddCommand
import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.aml.AmlCheckType.*
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class AmlCheckServiceSpec extends Specification {

  AmlService amlService = Mock()
  AmlCheckService amlCheckService = new AmlCheckService(amlService)

  private static AmlCheck check(AmlCheckType type) {
    return AmlCheck.builder().personalCode(sampleUser().build().personalCode).type(type).build()
  }


  @Unroll
  def "returns only missing checks"() {
    given:
    def user = sampleUser().build()
    when:
    def result = amlCheckService.getMissingChecks(user)
    then:
    result == missing
    1 * amlService.getChecks(user) >> checks
    where:
    checks                              | missing
    []                                  | [RESIDENCY_MANUAL, OCCUPATION, CONTACT_DETAILS]
    [check(DOCUMENT)]                   | [RESIDENCY_MANUAL, OCCUPATION, CONTACT_DETAILS]
    [check(RESIDENCY_AUTO)]             | [OCCUPATION, CONTACT_DETAILS]
    [check(RESIDENCY_MANUAL)]           | [OCCUPATION, CONTACT_DETAILS]
    [check(POLITICALLY_EXPOSED_PERSON)] | [RESIDENCY_MANUAL, OCCUPATION, CONTACT_DETAILS]
    [check(OCCUPATION)]                 | [RESIDENCY_MANUAL, CONTACT_DETAILS]
    [check(CONTACT_DETAILS)]            | [RESIDENCY_MANUAL, OCCUPATION]
    [
      check(RESIDENCY_MANUAL),
      check(OCCUPATION),
      check(CONTACT_DETAILS)
    ]                                   | []
    [
      check(RESIDENCY_AUTO),
      check(OCCUPATION),
      check(CONTACT_DETAILS)
    ]                                   | []
  }

  def "adds check if missing"() {
    given:
    def user = sampleUser().build()
    def command = AmlCheckAddCommand.builder().type(DOCUMENT).success(true).build()
    def amlCheck = AmlCheck.builder().personalCode(user.personalCode).type(DOCUMENT).success(true).build()
    when:
    amlCheckService.addCheckIfMissing(user, command)
    then:
    1 * amlService.addCheckIfMissing(amlCheck)
  }

  def "adds PEP check with success false when user declares they are a PEP"() {
    given:
    def user = sampleUser().build()
    def command = AmlCheckAddCommand.builder()
        .type(POLITICALLY_EXPOSED_PERSON)
        .success(false)
        .metadata([:])
        .build()
    def expectedCheck = AmlCheck.builder()
        .personalCode(user.personalCode)
        .type(POLITICALLY_EXPOSED_PERSON)
        .success(false)
        .metadata([:])
        .build()
    when:
    amlCheckService.addCheckIfMissing(user, command)
    then:
    1 * amlService.addCheckIfMissing(expectedCheck)
  }
}
