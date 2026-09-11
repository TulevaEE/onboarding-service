package ee.tuleva.onboarding.epis

import ee.tuleva.onboarding.country.Country
import ee.tuleva.onboarding.user.UserContacts.ContactSummary
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.epis.ContactDetailsFixture.contactDetailsFixture

class UserContactsAdapterSpec extends Specification {

  ContactDetailsService contactDetailsService = Mock()
  UserContactsAdapter adapter = new UserContactsAdapter(contactDetailsService)

  def "maps contact details to a contact summary field-for-field"() {
    given:
    def person = samplePerson()
    def contactDetails = contactDetailsFixture()
    1 * contactDetailsService.getContactDetails(person) >> contactDetails

    when:
    def result = adapter.forPerson(person)

    then:
    result == expectedSummaryFor(contactDetails)
  }

  def "maps missing optional contact details fields to null"() {
    given:
    def person = samplePerson()
    def contactDetails = new ContactDetails()
    1 * contactDetailsService.getContactDetails(person) >> contactDetails

    when:
    def result = adapter.forPerson(person)

    then:
    result == new ContactSummary(null, null, null, null, null, false, false, null, null, null)
  }

  def "fetches contact details using the given jwt token"() {
    given:
    def person = samplePerson()
    def jwtToken = "sample-jwt-token"
    def contactDetails = contactDetailsFixture()
    1 * contactDetailsService.getContactDetails(person, jwtToken) >> contactDetails

    when:
    def result = adapter.forPerson(person, jwtToken)

    then:
    result == expectedSummaryFor(contactDetails)
  }

  def "updates contact details and maps the result to a contact summary"() {
    given:
    def person = samplePerson()
    def address = Country.builder().countryCode("LV").build()
    def contactDetails = contactDetailsFixture()
    1 * contactDetailsService.updateContactDetails(person, "new@tuleva.ee", "5555555", address) >>
        contactDetails

    when:
    def result = adapter.update(person, "new@tuleva.ee", "5555555", address)

    then:
    result == expectedSummaryFor(contactDetails)
  }

  private static ContactSummary expectedSummaryFor(ContactDetails contactDetails) {
    new ContactSummary(
        contactDetails.email,
        contactDetails.phoneNumber,
        contactDetails.pensionAccountNumber,
        contactDetails.country,
        contactDetails.activeSecondPillarFundPik,
        contactDetails.secondPillarActive,
        contactDetails.thirdPillarActive,
        contactDetails.secondPillarOpenDate,
        contactDetails.thirdPillarInitDate,
        contactDetails.lastUpdateDate)
  }
}
