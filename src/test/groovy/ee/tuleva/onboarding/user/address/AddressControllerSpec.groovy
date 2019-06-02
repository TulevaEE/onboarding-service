package ee.tuleva.onboarding.user.address

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.Person
import org.springframework.http.MediaType

import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.user.address.AddressFixture.addressFixture
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class AddressControllerSpec extends BaseControllerSpec {

    def addressService = Mock(AddressService)

    def controller = new AddressController(addressService)

    def "update address"() {
        given:
        def address = addressFixture().build()
        addressService.updateAddress(_ as Person, address) >> contactDetailsFixture().setAddress(address)

        expect:
        mockMvc(controller)
            .perform(patch("/v1/me/address")
                .content(mapper.writeValueAsString(address))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath('$.street', is(address.street)))
            .andExpect(jsonPath('$.districtCode', is(address.districtCode)))
            .andExpect(jsonPath('$.postalCode', is(address.postalCode)))
            .andExpect(jsonPath('$.countryCode', is(address.countryCode)))
    }
}
