package ee.tuleva.onboarding.epis.contact


import ee.tuleva.onboarding.epis.EpisService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.user.address.AddressFixture.addressFixture

class ContactDetailsServiceSpec extends Specification {

    def episService = Mock(EpisService)

    def addressService = new ContactDetailsService(episService)

    def "Can update contact details"() {
        given:
        def user = sampleUser().build()
        def address = addressFixture().build()
        episService.getContactDetails(user) >> contactDetailsFixture()

        when:
        addressService.updateContactDetails(user, address)

        then:
        1 * episService.updateContactDetails({ person ->
            person == user
        }, { contactDetails ->
            contactDetails.email == user.email
            contactDetails.phoneNumber == user.phoneNumber
            contactDetails.addressRow1 == address.street
            contactDetails.country == address.countryCode
            contactDetails.districtCode == address.districtCode
            contactDetails.postalIndex == address.postalCode
        })
    }
}
