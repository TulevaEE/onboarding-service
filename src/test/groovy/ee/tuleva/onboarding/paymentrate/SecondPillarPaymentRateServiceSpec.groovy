package ee.tuleva.onboarding.paymentrate

import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.epis.contact.ContactDetails
import ee.tuleva.onboarding.epis.contact.ContactDetailsService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture

class SecondPillarPaymentRateServiceSpec extends Specification {

  def contactDetailsService = Mock(ContactDetailsService)
  def service = new SecondPillarPaymentRateService(contactDetailsService)

  def "getPaymentRates returns current and pending rates from contact details"() {
    given:
        Person person = samplePerson()
        def contactDetails = contactDetailsFixture()
        contactDetails.secondPillarPaymentRates = new ContactDetails.PaymentRates(6, 4)
        contactDetailsService.getContactDetails(person) >> contactDetails

    when:
        PaymentRates rates = service.getPaymentRates(person)

    then:
        rates.current == 6
        rates.pending.get() == 4
  }

  def "getPaymentRates returns only current rate when no pending"() {
    given:
        Person person = samplePerson()
        def contactDetails = contactDetailsFixture()
        contactDetails.secondPillarPaymentRates = new ContactDetails.PaymentRates(6, null)
        contactDetailsService.getContactDetails(person) >> contactDetails

    when:
        PaymentRates rates = service.getPaymentRates(person)

    then:
        rates.current == 6
        rates.pending == Optional.empty()
  }

  def "getPaymentRates defaults to 2 when contact details rates are null"() {
    given:
        Person person = samplePerson()
        def contactDetails = contactDetailsFixture()
        contactDetails.secondPillarPaymentRates = null
        contactDetailsService.getContactDetails(person) >> contactDetails

    when:
        PaymentRates rates = service.getPaymentRates(person)

    then:
        rates.current == 2
        rates.pending == Optional.empty()
  }

}
