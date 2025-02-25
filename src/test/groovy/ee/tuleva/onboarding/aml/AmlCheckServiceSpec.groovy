package ee.tuleva.onboarding.aml

import ee.tuleva.onboarding.aml.dto.AmlCheckAddCommand
import ee.tuleva.onboarding.epis.EpisService
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant

import static ee.tuleva.onboarding.aml.AmlCheckType.*
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.recentlyUpdatedContactDetailsFixture
import static java.time.temporal.ChronoUnit.DAYS

class AmlCheckServiceSpec extends Specification {

  AmlService amlService = Mock()
  EpisService episService = Mock()
  AmlCheckService amlCheckService = new AmlCheckService(amlService, episService)

  private static AmlCheck check(AmlCheckType type) {
    return AmlCheck.builder().personalCode(sampleUser().build().personalCode).type(type).build()
  }

  private static AmlCheck check(AmlCheckType type, Instant createdTime) {
    return AmlCheck.builder().personalCode(sampleUser().build().personalCode).type(type).createdTime(createdTime).build()
  }


  @Unroll
  def "returns only missing checks"() {
    given:
    def user = sampleUser().build()
    when:
    episService.getContactDetails(user) >> recentlyUpdatedContactDetailsFixture()
    amlService.getChecks(user) >> checks
    def result = amlCheckService.getMissingChecks(user)
    then:
    result == missing
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

  def "adds contact details check if required by EPIS"() {
    given:
    def user = sampleUser().build()
    when:
    1 * episService.getContactDetails(user) >> contactDetailsFixture()
    1 * amlService.getChecks(user) >> [check(CONTACT_DETAILS)]

    List<AmlCheckType> result = amlCheckService.getMissingChecks(user)
    then:
    result.contains(CONTACT_DETAILS)
  }


  def "does not add contact details check if not required by EPIS and done recently"() {
    given:
    def user = sampleUser().build()
    when:
    1 * episService.getContactDetails(user) >> recentlyUpdatedContactDetailsFixture()
    1 * amlService.getChecks(user) >> [check(CONTACT_DETAILS)]

    List<AmlCheckType> result = amlCheckService.getMissingChecks(user)
    then:
    !result.contains(CONTACT_DETAILS)
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
}
