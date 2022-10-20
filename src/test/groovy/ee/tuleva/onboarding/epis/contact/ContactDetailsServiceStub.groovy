package ee.tuleva.onboarding.epis.contact

import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.PersonFixture.sampleToken
import static ee.tuleva.onboarding.auth.UserFixture.getSampleUser
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.user.address.AddressFixture.getAnAddress

class ContactDetailsServiceStub extends Specification {

  static ContactDetailsService stubContactDetailsService() {
    return new ContactDetailsServiceStub().get()
  }

  ContactDetailsService get() {
    return Stub(ContactDetailsService) {
      updateContactDetails(sampleUser, anAddress) >> contactDetailsFixture()
      getContactDetails(samplePerson) >> contactDetailsFixture()
      getContactDetails(samplePerson, sampleToken) >> contactDetailsFixture()
    }
  }

}
