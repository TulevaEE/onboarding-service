package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.user.address.AddressService
import ee.tuleva.onboarding.user.command.CreateUserCommand
import ee.tuleva.onboarding.user.command.UpdateUserCommand
import org.springframework.http.MediaType

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.user.address.AddressFixture.addressFixture
import static org.hamcrest.Matchers.*
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class UserControllerSpec extends BaseControllerSpec {

    UserService userService = Mock()
    EpisService episService = Mock()
    AddressService addressService = Mock()

    UserController controller = new UserController(userService, episService, addressService)

    def "/me endpoint works with non member"() {
        given:
        def contactDetails = contactDetailsFixture()
        1 * userService.getById(sampleAuthenticatedPerson.userId) >> userFrom(sampleAuthenticatedPerson)
        1 * episService.getContactDetails(sampleAuthenticatedPerson) >> contactDetails

        expect:
        mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)
            .perform(get("/v1/me"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath('$.id', is(2)))
            .andExpect(jsonPath('$.firstName', is(sampleAuthenticatedPerson.firstName)))
            .andExpect(jsonPath('$.lastName', is(sampleAuthenticatedPerson.lastName)))
            .andExpect(jsonPath('$.personalCode', is(sampleAuthenticatedPerson.personalCode)))
            .andExpect(jsonPath('$.age', isA(Integer)))
            .andExpect(jsonPath('$.email', is(nullValue())))
            .andExpect(jsonPath('$.phoneNumber', is(nullValue())))
            .andExpect(jsonPath('$.memberNumber', is(nullValue())))
            .andExpect(jsonPath('$.pensionAccountNumber', is(contactDetails.pensionAccountNumber)))
            .andExpect(jsonPath('$.address.street', is(contactDetails.addressRow1)))
            .andExpect(jsonPath('$.address.districtCode', is(contactDetails.districtCode)))
            .andExpect(jsonPath('$.address.postalCode', is(contactDetails.postalIndex)))
            .andExpect(jsonPath('$.address.countryCode', is(contactDetails.country)))
    }

    def "/me endpoint works with a member"() {
        given:
        def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        def user = sampleUser().build()
        def contactDetails = contactDetailsFixture()
        1 * userService.getById(user.id) >> user
        1 * episService.getContactDetails(authenticatedPerson) >> contactDetails

        expect:
        mockMvcWithAuthenticationPrincipal(authenticatedPerson, controller)
            .perform(get("/v1/me"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath('$.id', is(authenticatedPerson.userId.intValue())))
            .andExpect(jsonPath('$.firstName', is(authenticatedPerson.firstName)))
            .andExpect(jsonPath('$.lastName', is(authenticatedPerson.lastName)))
            .andExpect(jsonPath('$.personalCode', is(authenticatedPerson.personalCode)))
            .andExpect(jsonPath('$.age', is(user.age)))
            .andExpect(jsonPath('$.email', is(user.email)))
            .andExpect(jsonPath('$.phoneNumber', is(user.phoneNumber)))
            .andExpect(jsonPath('$.memberNumber', is(user.member.get().memberNumber)))
            .andExpect(jsonPath('$.pensionAccountNumber', is(contactDetails.pensionAccountNumber)))
            .andExpect(jsonPath('$.address.street', is(contactDetails.addressRow1)))
            .andExpect(jsonPath('$.address.districtCode', is(contactDetails.districtCode)))
            .andExpect(jsonPath('$.address.postalCode', is(contactDetails.postalIndex)))
            .andExpect(jsonPath('$.address.countryCode', is(contactDetails.country)))
    }

    def "updates an existing user"() {
        given:
        def contactDetails = contactDetailsFixture()
        def address = addressFixture().build()
        def command = new UpdateUserCommand(
            email: "erko@risthein.ee",
            phoneNumber: "5555555",
            address: address
        )

        1 * userService.updateUser(sampleAuthenticatedPerson.personalCode, command.email, command.phoneNumber) >>
            userFrom(sampleAuthenticatedPerson, command)
        1 * addressService.updateAddress(sampleAuthenticatedPerson, command.address) >> contactDetails.setAddress(address)

        def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)

        when:
        def performCall = mvc
            .perform(patch("/v1/me")
                .content(mapper.writeValueAsString(command))
                .contentType(MediaType.APPLICATION_JSON))

        then:
        performCall.andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath('$.firstName', is("Erko")))
            .andExpect(jsonPath('$.lastName', is("Risthein")))
            .andExpect(jsonPath('$.personalCode', is("38501010002")))
            .andExpect(jsonPath('$.email', is("erko@risthein.ee")))
            .andExpect(jsonPath('$.phoneNumber', is("5555555")))
            .andExpect(jsonPath('$.age', isA(Integer)))
            .andExpect(jsonPath('$.pensionAccountNumber', is(contactDetails.pensionAccountNumber)))
            .andExpect(jsonPath('$.address.street', is(address.street)))
            .andExpect(jsonPath('$.address.districtCode', is(address.districtCode)))
            .andExpect(jsonPath('$.address.postalCode', is(address.postalCode)))
            .andExpect(jsonPath('$.address.countryCode', is(address.countryCode)))
    }

    def "validates a new user before saving"() {
        given:
        def command = new UpdateUserCommand()
        def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)

        when:
        def performCall = mvc
            .perform(patch("/v1/me")
                .content(mapper.writeValueAsString(command))
                .contentType(MediaType.APPLICATION_JSON))

        then:
        0 * userService.updateUser(*_)
        performCall.andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath('$.errors', hasSize(1)))
    }

    def "create a new user"() {
        given:
        def command = new CreateUserCommand(
            personalCode: "38501010002",
            email: "erko@risthein.ee",
            phoneNumber: "5555555")
        def mvc = mockMvc(controller)

        when:
        def performCall = mvc
            .perform(post("/v1/users")
                .content(mapper.writeValueAsString(command))
                .contentType(MediaType.APPLICATION_JSON))

        then:
        1 * userService.createOrUpdateUser(command.personalCode, command.email, command.phoneNumber) >>
            userFrom(command)
        performCall.andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath('$.personalCode', is("38501010002")))
            .andExpect(jsonPath('$.email', is("erko@risthein.ee")))
            .andExpect(jsonPath('$.phoneNumber', is("5555555")))
    }

    private User userFrom(AuthenticatedPerson authenticatedPerson, UpdateUserCommand command = null) {
        User.builder()
            .id(authenticatedPerson.userId)
            .firstName(authenticatedPerson.firstName)
            .lastName(authenticatedPerson.lastName)
            .personalCode(authenticatedPerson.personalCode)
            .email(command?.email)
            .phoneNumber(command?.phoneNumber)
            .build()
    }

    private User userFrom(CreateUserCommand command) {
        User.builder()
            .personalCode(command.personalCode)
            .email(command.email)
            .phoneNumber(command.phoneNumber)
            .build()
    }

    AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
        .firstName("Erko")
        .lastName("Risthein")
        .personalCode("38501010002")
        .userId(2L)
        .build()
}
