package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.kpr.KPRClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import spock.lang.Specification

import java.time.Instant

import static org.hamcrest.Matchers.is
import static org.hamcrest.Matchers.isA
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

@SpringBootTest
class UserControllerSpec extends Specification {

	@Autowired
	MappingJackson2HttpMessageConverter jacksonMessageConverter

	MockMvc mvc;
	UserController userController = new UserController(Mock(KPRClient))


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

		mvc = mockMvcWithAuthenticationPrincipal(user)

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

	private MockMvc mockMvcWithAuthenticationPrincipal(User user) {
		standaloneSetup(new UserController(Mock(KPRClient)))
				.setMessageConverters(jacksonMessageConverter)
				.setCustomArgumentResolvers(authenticationPrincipalResolver(user))
				.build()
	}

	HandlerMethodArgumentResolver authenticationPrincipalResolver(User user) {
		return new HandlerMethodArgumentResolver() {
			@Override
			boolean supportsParameter(MethodParameter parameter) {
				return parameter.parameterType == User
			}

			@Override
			Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
				return user
			}
		}
	}

}
