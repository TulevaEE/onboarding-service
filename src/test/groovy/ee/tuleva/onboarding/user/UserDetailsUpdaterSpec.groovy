package ee.tuleva.onboarding.user


import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent
import ee.tuleva.onboarding.epis.contact.ContactDetailsService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture

class UserDetailsUpdaterSpec extends Specification {

  UserService userService = Mock()
  ContactDetailsService contactDetailsService = Mock()

  UserDetailsUpdater service = new UserDetailsUpdater(userService, contactDetailsService)

  def "updates user email and phone number based on epis info"() {
    given:
    def user = sampleUser().email(null).phoneNumber(null).build()
    def jwtToken = "token"
    def contactDetails = contactDetailsFixture()
    1 * userService.findByPersonalCode(user.personalCode) >> Optional.of(user)
    1 * contactDetailsService.getContactDetails(user, jwtToken) >> contactDetails

    when:
    service.onAfterTokenGrantedEvent(new AfterTokenGrantedEvent(this, user, jwtToken))

    then:
    1 * userService.updateUser(user.personalCode, Optional.of(contactDetails.email), contactDetails.phoneNumber)
  }
}
