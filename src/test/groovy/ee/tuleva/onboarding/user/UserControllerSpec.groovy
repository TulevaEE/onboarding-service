package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.role.Role
import ee.tuleva.onboarding.auth.role.RoleType
import ee.tuleva.onboarding.paymentrate.PaymentRates
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService
import ee.tuleva.onboarding.user.command.UpdateUserCommand
import org.springframework.http.MediaType

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON
import static ee.tuleva.onboarding.user.UserContactsFixture.contactSummaryFixture
import static ee.tuleva.onboarding.country.CountryFixture.countryFixture
import static org.hamcrest.Matchers.*
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class UserControllerSpec extends BaseControllerSpec {

  UserService userService = Mock()
  UserContacts userContacts = Mock()
  SecondPillarPaymentRateService secondPillarPaymentRateService = Mock()

  UserController controller = new UserController(
      userService, userContacts, secondPillarPaymentRateService)

  def "/me endpoint works with non member"() {
    given:
    def contactSummary = contactSummaryFixture()
    def user = userFrom(sampleAuthenticatedPerson)
    def samplePaymentRates = new PaymentRates(2, 6)
    1 * userService.getById(sampleAuthenticatedPerson.userId) >> Optional.of(user)
    1 * userContacts.forPerson(sampleAuthenticatedPerson) >> contactSummary
    1 * secondPillarPaymentRateService
        .getPaymentRates(sampleAuthenticatedPerson) >> samplePaymentRates

    expect:
    mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)
        .perform(get("/v1/me"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.id', is(2)))
        .andExpect(jsonPath('$.firstName', is(sampleAuthenticatedPerson.firstName)))
        .andExpect(jsonPath('$.lastName', is(sampleAuthenticatedPerson.lastName)))
        .andExpect(jsonPath('$.personalCode', is(sampleAuthenticatedPerson.personalCode)))
        .andExpect(jsonPath('$.age', is(user.age)))
        .andExpect(jsonPath('$.email', is(user.email)))
        .andExpect(jsonPath('$.phoneNumber', is(user.phoneNumber)))
        .andExpect(jsonPath('$.memberNumber', is(nullValue())))
        .andExpect(jsonPath('$.pensionAccountNumber', is(contactSummary.pensionAccountNumber())))
        .andExpect(jsonPath('$.address.countryCode', is(contactSummary.country())))
        .andExpect(jsonPath('$.secondPillarActive', is(contactSummary.secondPillarActive())))
        .andExpect(jsonPath('$.thirdPillarActive', is(contactSummary.thirdPillarActive())))
        .andExpect(jsonPath('$.secondPillarPaymentRates.pending',
            is(samplePaymentRates.pending.get())))
        .andExpect(jsonPath('$.secondPillarPaymentRates.current',
            is(samplePaymentRates.current)))
        .andExpect(jsonPath('$.memberJoinDate', is(nullValue())))
        .andExpect(jsonPath('$.secondPillarOpenDate', is(contactSummary.secondPillarOpenDate().toString())))
        .andExpect(jsonPath('$.thirdPillarInitDate', is(contactSummary.thirdPillarInitDate().toString())))
        .andExpect(jsonPath('$.contactDetailsLastUpdateDate', is(contactSummary.lastUpdateDate().toString())))
  }

  def "serialized no payment rate correctly as null"() {
    given:
    def contactSummary = contactSummaryFixture()
    def user = userFrom(sampleAuthenticatedPerson)
    def samplePaymentRates = new PaymentRates(2, null)
    1 * userService.getById(sampleAuthenticatedPerson.userId) >> Optional.of(user)
    1 * userContacts.forPerson(sampleAuthenticatedPerson) >> contactSummary
    1 * secondPillarPaymentRateService
        .getPaymentRates(sampleAuthenticatedPerson) >> samplePaymentRates

    expect:
    mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)
        .perform(get("/v1/me"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.id', is(2)))
        .andExpect(jsonPath('$.firstName', is(sampleAuthenticatedPerson.firstName)))
        .andExpect(jsonPath('$.lastName', is(sampleAuthenticatedPerson.lastName)))
        .andExpect(jsonPath('$.personalCode', is(sampleAuthenticatedPerson.personalCode)))
        .andExpect(jsonPath('$.age', is(user.age)))
        .andExpect(jsonPath('$.email', is(user.email)))
        .andExpect(jsonPath('$.phoneNumber', is(user.phoneNumber)))
        .andExpect(jsonPath('$.memberNumber', is(nullValue())))
        .andExpect(jsonPath('$.pensionAccountNumber', is(contactSummary.pensionAccountNumber())))
        .andExpect(jsonPath('$.address.countryCode', is(contactSummary.country())))
        .andExpect(jsonPath('$.secondPillarActive', is(contactSummary.secondPillarActive())))
        .andExpect(jsonPath('$.thirdPillarActive', is(contactSummary.thirdPillarActive())))
        .andExpect(jsonPath('$.secondPillarPaymentRates.pending',
            is(null)))
        .andExpect(jsonPath('$.secondPillarPaymentRates.current',
            is(samplePaymentRates.current)))
        .andExpect(jsonPath('$.memberJoinDate', is(nullValue())))
  }

  def "/me endpoint works with a member"() {
    given:
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    def user = sampleUser().build()
    def contactSummary = contactSummaryFixture()
    def samplePaymentRates = new PaymentRates(2, 6)
    1 * userService.getById(user.id) >> Optional.of(user)
    1 * userContacts.forPerson(authenticatedPerson) >> contactSummary
    1 * secondPillarPaymentRateService
        .getPaymentRates(authenticatedPerson) >> samplePaymentRates

    expect:
    mockMvcWithAuthenticationPrincipal(authenticatedPerson, controller)
        .perform(get("/v1/me"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.id', is(authenticatedPerson.userId.intValue())))
        .andExpect(jsonPath('$.firstName', is(authenticatedPerson.firstName)))
        .andExpect(jsonPath('$.lastName', is(authenticatedPerson.lastName)))
        .andExpect(jsonPath('$.personalCode', is(authenticatedPerson.personalCode)))
        .andExpect(jsonPath('$.age', is(user.age)))
        .andExpect(jsonPath('$.email', is(user.email)))
        .andExpect(jsonPath('$.phoneNumber', is(user.phoneNumber)))
        .andExpect(jsonPath('$.memberNumber', is(user.memberOrThrow.memberNumber)))
        .andExpect(jsonPath('$.pensionAccountNumber', is(contactSummary.pensionAccountNumber())))
        .andExpect(jsonPath('$.address.countryCode', is(contactSummary.country())))
        .andExpect(jsonPath('$.secondPillarPaymentRates.pending',
            is(samplePaymentRates.pending.get())))
        .andExpect(jsonPath('$.secondPillarPaymentRates.current',
            is(samplePaymentRates.current)))
        .andExpect(jsonPath('$.memberJoinDate', is(user.memberOrThrow.createdDate.toString())))
  }

  def "/me/principal endpoint works"() {
    expect:
    mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)
        .perform(get("/v1/me/principal"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.userId', is(2)))
        .andExpect(jsonPath('$.firstName', is(sampleAuthenticatedPerson.firstName)))
        .andExpect(jsonPath('$.lastName', is(sampleAuthenticatedPerson.lastName)))
        .andExpect(jsonPath('$.personalCode', is(sampleAuthenticatedPerson.personalCode)))
  }

  def "updates an existing user"() {
    given:
    def address = countryFixture().build()
    def contactSummary = contactSummaryFixture(address.countryCode)
    def command = new UpdateUserCommand("erko@risthein.ee", "5555555", address)
    def updatedUser = userFrom(sampleAuthenticatedPerson, command)
    def samplePaymentRates = new PaymentRates(2, 6)

    1 * userService
        .updateUser(sampleAuthenticatedPerson.personalCode, Optional.of(command.email), command.phoneNumber) >>
        updatedUser
    1 * userContacts.update(updatedUser, updatedUser.email, updatedUser.phoneNumber, command.address) >>
        contactSummary
    1 * secondPillarPaymentRateService
        .getPaymentRates(sampleAuthenticatedPerson) >> samplePaymentRates

    def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)

    when:
    def performCall = mvc
        .perform(patch("/v1/me")
            .content(mapper.writeValueAsString(command))
            .contentType(MediaType.APPLICATION_JSON))

    then:
    performCall.andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.firstName', is("Erko")))
        .andExpect(jsonPath('$.lastName', is("Risthein")))
        .andExpect(jsonPath('$.personalCode', is("38501010002")))
        .andExpect(jsonPath('$.email', is("erko@risthein.ee")))
        .andExpect(jsonPath('$.phoneNumber', is("5555555")))
        .andExpect(jsonPath('$.age', isA(Integer)))
        .andExpect(jsonPath('$.pensionAccountNumber', is(contactSummary.pensionAccountNumber())))
        .andExpect(jsonPath('$.address.countryCode', is(address.countryCode)))
        .andExpect(jsonPath('$.secondPillarPaymentRates.pending',
            is(samplePaymentRates.pending.get())))
        .andExpect(jsonPath('$.secondPillarPaymentRates.current',
            is(samplePaymentRates.current)))
  }

  def "can update just email and phone number"() {
    given:
    def command = new UpdateUserCommand("erko@risthein.ee", "5555555", null)
    def updatedUser = userFrom(sampleAuthenticatedPerson, command)

    1 * userService
        .updateUser(sampleAuthenticatedPerson.personalCode, Optional.of(command.email), command.phoneNumber) >>
        updatedUser
    0 * userContacts.update(_, _, _, _)

    def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)

    when:
    def performCall = mvc
        .perform(patch("/v1/me")
            .content(mapper.writeValueAsString(command))
            .contentType(MediaType.APPLICATION_JSON))

    then:
    performCall.andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.firstName', is("Erko")))
        .andExpect(jsonPath('$.lastName', is("Risthein")))
        .andExpect(jsonPath('$.personalCode', is("38501010002")))
        .andExpect(jsonPath('$.email', is("erko@risthein.ee")))
        .andExpect(jsonPath('$.phoneNumber', is("5555555")))
        .andExpect(jsonPath('$.age', isA(Integer)))
        .andExpect(jsonPath('$.pensionAccountNumber', is(null)))
        .andExpect(jsonPath('$.address', is(null)))
  }

  def "validates a new user before saving"() {
    given:
    def command = new UpdateUserCommand(null, null, null)
    def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)

    when:
    def performCall = mvc
        .perform(patch("/v1/me")
            .content(mapper.writeValueAsString(command))
            .contentType(MediaType.APPLICATION_JSON))

    then:
    0 * userService.updateUser(*_)
    performCall.andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.errors', hasSize(1)))
  }

  private static User userFrom(AuthenticatedPerson authenticatedPerson, UpdateUserCommand command = null) {
    sampleUserNonMember()
        .id(authenticatedPerson.userId)
        .firstName(authenticatedPerson.firstName)
        .lastName(authenticatedPerson.lastName)
        .personalCode(authenticatedPerson.personalCode)
        .email(command?.email)
        .phoneNumber(command?.phoneNumber)
        .build()
  }

  AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
      .firstName("Erko")
      .lastName("Risthein")
      .personalCode("38501010002")
      .userId(2L)
      .role(new Role(PERSON, "38501010002", "Erko Risthein"))
      .build()
}
