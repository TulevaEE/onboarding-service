package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.user.command.CreateUserCommand
import ee.tuleva.onboarding.user.preferences.CsdUserPreferencesService
import ee.tuleva.onboarding.user.preferences.UserPreferences
import org.springframework.http.MediaType

import static org.hamcrest.Matchers.hasSize
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class UserControllerSpec extends BaseControllerSpec {

	CsdUserPreferencesService preferencesService = Mock(CsdUserPreferencesService)
	UserRepository userRepository = Mock(UserRepository)

	UserController controller = new UserController(preferencesService, userRepository)

	def "/me endpoint works"() {
		given:

		def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)

		expect:
		mvc.perform(get("/v1/me"))
 				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
				.andExpect(jsonPath('$.firstName', is("Erko")))
				.andExpect(jsonPath('$.lastName', is("Risthein")))
				.andExpect(jsonPath('$.age', is(32)))
	}

	def "/preferences endpoint works"() {
		given:
		1 * preferencesService.getPreferences(sampleAuthenticatedPerson.user.get().personalCode) >> UserPreferences.builder().addressRow1("Telliskivi").build()

		def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)

		expect:
		mvc.perform(get("/v1/preferences"))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
				.andExpect(jsonPath('$.addressRow1', is("Telliskivi")))
	}

	def "saves a new user"() {
		given:
		def command = new CreateUserCommand(
				firstName: "Erko",
				lastName: "Risthein",
				personalCode: "38501010002",
				email: "erko@risthein.ee",
				phoneNumber: "5555555")
		def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)

		when:
		def performCall = mvc
				.perform(post("/v1/users")
				.content(mapper.writeValueAsString(command))
				.contentType(MediaType.APPLICATION_JSON))

		then:
		1 * userRepository.save(_ as User) >> userFrom(command)
		performCall.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
				.andExpect(jsonPath('$.firstName', is("Erko")))
				.andExpect(jsonPath('$.lastName', is("Risthein")))
				.andExpect(jsonPath('$.personalCode', is("38501010002")))
				.andExpect(jsonPath('$.email', is("erko@risthein.ee")))
				.andExpect(jsonPath('$.phoneNumber', is("5555555")))
	}

	private User userFrom(CreateUserCommand command) {
		User.builder()
				.firstName(command.firstName)
				.lastName(command.lastName)
				.personalCode(command.personalCode)
				.email(command.email)
				.phoneNumber(command.phoneNumber)
				.build()
	}

	def "validates a new user before saving"() {
		given:
		def command = new CreateUserCommand()
		def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)

		when:
		def performCall = mvc
				.perform(post("/v1/users")
				.content(mapper.writeValueAsString(command))
				.contentType(MediaType.APPLICATION_JSON))

		then:
		0 * userRepository.save(userFrom(command))
		performCall.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
				.andExpect(jsonPath('$.errors', hasSize(4)))
	}

	AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
			.firstName("Erko")
			.lastName("Risthein")
			.personalCode("38501010002")
			.user(User.builder()
			.firstName("Erko")
			.lastName("Risthein")
			.personalCode("38501010002")
			.build()
	).build()
}
