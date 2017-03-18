package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.BaseControllerSpec
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import java.time.Instant

import static org.hamcrest.Matchers.is
import static org.hamcrest.Matchers.isA
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class UserControllerSpec extends BaseControllerSpec {

	CsdUserPreferencesService preferencesService = Mock(CsdUserPreferencesService)

	UserController controller = new UserController(preferencesService)

	MockMvc mvc

	def "/me endpoint works"() {
		given:
		User user = User.builder()
				.id(1L)
				.firstName("Erko")
				.lastName("Risthein")
				.personalCode("38501010002")
				.createdDate(Instant.parse("2017-01-31T14:06:01Z"))
				.memberNumber(3000)
				.build()

		mvc = mockMvcWithAuthenticationPrincipal(user, controller)

		expect:
		mvc.perform(get("/v1/me"))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
				.andExpect(jsonPath('$.id', is(1)))
				.andExpect(jsonPath('$.firstName', is("Erko")))
				.andExpect(jsonPath('$.lastName', is("Risthein")))
				.andExpect(jsonPath('$.personalCode', is("38501010002")))
				.andExpect(jsonPath('$.createdDate', is("2017-01-31T14:06:01Z")))
				.andExpect(jsonPath('$.memberNumber', is(3000)))
				.andExpect(jsonPath('$.age', isA(Integer)))
	}

	def "/prefereces endpoint works"() {
		given:
		1 * preferencesService.getPreferences(*_) >> UserPreferences.builder().addressRow1("Telliskivi").build()
		User user = User.builder()
				.id(1L)
				.firstName("Erko")
				.memberNumber(3000)
				.build()

		mvc = mockMvcWithAuthenticationPrincipal(user, controller)

		expect:
		mvc.perform(get("/v1/preferences"))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
				.andExpect(jsonPath('$.addressRow1', is("Telliskivi")))

	}


}
