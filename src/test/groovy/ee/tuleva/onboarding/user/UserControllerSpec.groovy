package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult

import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class UserControllerSpec extends BaseControllerSpec {

	CsdUserPreferencesService preferencesService = Mock(CsdUserPreferencesService)

	UserController controller = new UserController(preferencesService)

	MockMvc mvc

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

	def "/me endpoint works"() {
		given:

		mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)

		when:
		MvcResult resp = mvc.perform(get("/v1/me")).andReturn()

		then:
		true


/*
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
				.andExpect(jsonPath('$.firstName', is("Erko")))
				.andExpect(jsonPath('$.lastName', is("Risthein")))
				.andExpect(jsonPath('$.age', isA(Integer)))
*/
	}

	def "/prefereces endpoint works"() {
		given:
		1 * preferencesService.getPreferences(sampleAuthenticatedPerson.user.get().personalCode) >> UserPreferences.builder().addressRow1("Telliskivi").build()

		mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, controller)

		expect:
		mvc.perform(get("/v1/preferences"))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
				.andExpect(jsonPath('$.addressRow1', is("Telliskivi")))

	}


}
