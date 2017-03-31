package ee.tuleva.onboarding

import ee.tuleva.onboarding.user.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.MethodParameter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

import java.time.Instant

import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class UserProvidedControllerSpec extends BaseControllerSpec {

    MappingJackson2HttpMessageConverter jacksonMessageConverter = new MappingJackson2HttpMessageConverter();

    User user = User.builder()
            .id(1L)
            .firstName("Erko")
            .lastName("Risthein")
            .personalCode("38501010002")
            .createdDate(Instant.parse("2017-01-31T14:06:01Z"))
            .memberNumber(3000)
            .build()


    protected MockMvc mockMvcWithAuthenticationPrincipal(Object... controllers) {
        standaloneSetup(controllers)
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
