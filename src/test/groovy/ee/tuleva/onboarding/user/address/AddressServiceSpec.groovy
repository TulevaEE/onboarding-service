package ee.tuleva.onboarding.user.address

import ee.tuleva.onboarding.epis.EpisService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.user.address.AddressFixture.addressFixture

class AddressServiceSpec extends Specification {

    def episService = Mock(EpisService)

    def addressService = new AddressService(episService)

    def "Can update address"() {
        given:
        def person = samplePerson
        def address = addressFixture()
            .street("Sample street")
            .postalCode("Sample postal code")
            .countryCode("Sample country code")
            .districtCode("Sample district code")
            .build()
        episService.getContactDetails(person) >> contactDetailsFixture()

        when:
        addressService.updateAddress(person, address)

        then:
        1 * episService.updateContactDetails({ contactDetails ->
            contactDetails.addressRow1 == address.street
            contactDetails.country == address.countryCode
            contactDetails.districtCode == address.districtCode
            contactDetails.postalIndex == address.postalCode
        })
    }
}
