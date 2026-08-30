package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture
import ee.tuleva.onboarding.auth.AuthenticationTokens
import ee.tuleva.onboarding.auth.GrantType
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.*
import static ee.tuleva.onboarding.auth.GrantType.SMART_ID
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.user.UserContactsFixture.contactSummaryFixture

class UserDetailsUpdaterSpec extends Specification {

  UserService userService = Mock()
  UserContacts userContacts = Mock()

  UserDetailsUpdater service = new UserDetailsUpdater(userService, userContacts)

  def "updates user name from the auth provider on login, capitalizing it"() {
    given:
    def user = sampleUser().firstName("Jaak").lastName("Kadakas").build()
    def person = sampleAuthenticatedPersonAndMember()
        .firstName("JAAK")
        .lastName("KUUSK-ÕUNAPUU")
        .build()
    1 * userService.findByPersonalCode(person.personalCode) >> Optional.of(user)

    when:
    service.updateUserName(new AfterTokenGrantedEvent(this, person, SMART_ID, new AuthenticationTokens("access token", "refresh token")))

    then:
    1 * userService.save(user)
    user.firstName == "Jaak"
    user.lastName == "Kuusk-Õunapuu"
  }

  def "updates user email and phone number based on epis info"() {
    given:
    def user = sampleUser().email(null).phoneNumber(null).build()
    def person = sampleAuthenticatedPersonAndMember().build()
    def grantType = SMART_ID
    def tokens = new AuthenticationTokens("access token", "refresh token")
    def contactSummary = contactSummaryFixture()
    1 * userService.findByPersonalCode(person.personalCode) >> Optional.of(user)
    1 * userContacts.forPerson(person, tokens.accessToken()) >> contactSummary

    when:
    service.onAfterTokenGrantedEvent(new AfterTokenGrantedEvent(this, person, grantType, tokens))

    then:
    1 * userService.updateUser(user.personalCode, Optional.of(contactSummary.email()), contactSummary.phoneNumber())
  }
}
