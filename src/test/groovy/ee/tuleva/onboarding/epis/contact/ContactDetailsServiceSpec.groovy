package ee.tuleva.onboarding.epis.contact

import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.contact.event.ContactDetailsUpdatedEvent
import ee.tuleva.onboarding.error.exception.ErrorsResponseException
import ee.tuleva.onboarding.error.response.ErrorsResponse
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.country.CountryFixture.countryFixture

class ContactDetailsServiceSpec extends Specification {

  def episService = Mock(EpisService)
  def eventPublisher = Mock(ApplicationEventPublisher)

  def contactDetailsService = new ContactDetailsService(episService, eventPublisher)

  def "Can update contact details"() {
    given:
    def user = sampleUser().build()
    def address = countryFixture().build()
    episService.getContactDetails(user) >> contactDetailsFixture()

    when:
    contactDetailsService.updateContactDetails(user, address)

    then:
    1 * episService.updateContactDetails({ person ->
      person == user
    }, { contactDetails ->
      contactDetails.email == user.email
      contactDetails.phoneNumber == user.phoneNumber
      contactDetails.country == address.countryCode
    })
    1 * eventPublisher.publishEvent(_ as ContactDetailsUpdatedEvent)
  }

  def "can get contact details with token"() {
    given:
    def person = samplePerson()
    def token = "123"
    when:
    contactDetailsService.getContactDetails(person, token)
    then:
    1 * episService.getContactDetails(person, token)
  }

  def "can get contact details with no token"() {
    given:
    def person = samplePerson()
    when:
    contactDetailsService.getContactDetails(person)
    then:
    1 * episService.getContactDetails(person)
  }

  def "can update contact details for users with no pension account"() {
    given:
    def user = sampleUser().build()
    def address = countryFixture().build()
    def contactDetails = contactDetailsFixture()
    episService.getContactDetails(user) >> contactDetails

    1 * episService.updateContactDetails(user, _ as ContactDetails) >> {
      throw new ErrorsResponseException(
          ErrorsResponse.ofSingleError("40544", "Person has no active pension account!")
      )
    }

    when:
    def returnedContactDetails = contactDetailsService.updateContactDetails(user, address)

    then:
    1 * eventPublisher.publishEvent(_ as ContactDetailsUpdatedEvent)
    returnedContactDetails == contactDetails
  }
}
