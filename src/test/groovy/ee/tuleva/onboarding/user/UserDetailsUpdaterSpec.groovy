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

  def "changes the user name to the one the login identified, capitalized, naming the login"() {
    given:
    def person = sampleAuthenticatedPersonAndMember()
        .firstName("JAAK")
        .lastName("KUUSK-ÕUNAPUU")
        .build()

    when:
    service.updateUserName(new AfterTokenGrantedEvent(this, person, SMART_ID, new AuthenticationTokens("access token", "refresh token")))

    then:
    1 * userService.changeName(person.personalCode, "Jaak", "Kuusk-Õunapuu", NameChangeBasis.login(SMART_ID))
    0 * userService.save(_)
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
    1 * userService.fillMissingContactDetails(user.personalCode, Optional.of(contactSummary.email()), contactSummary.phoneNumber())
  }
}
