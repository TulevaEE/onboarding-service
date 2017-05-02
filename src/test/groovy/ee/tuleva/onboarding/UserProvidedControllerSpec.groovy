package ee.tuleva.onboarding

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.user.User
import org.springframework.core.MethodParameter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class UserProvidedControllerSpec extends BaseControllerSpec {

    MappingJackson2HttpMessageConverter jacksonMessageConverter = new MappingJackson2HttpMessageConverter();

    AuthenticatedPerson authenticatedPerson = AuthenticatedPerson.builder()
        .firstName("Erko")
        .lastName("Risthein")
        .userId(1L)
        .build()

    protected MockMvc mockMvcWithAuthenticationPrincipal(Object... controllers) {
        standaloneSetup(controllers)
                .setMessageConverters(jacksonMessageConverter)
                .setCustomArgumentResolvers(authenticationPrincipalResolver(authenticatedPerson))
                .build()
    }

    HandlerMethodArgumentResolver authenticationPrincipalResolver(AuthenticatedPerson authenticatedPerson) {
        return new HandlerMethodArgumentResolver() {
            @Override
            boolean supportsParameter(MethodParameter parameter) {
                return parameter.parameterType == User
            }

            @Override
            Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
                return authenticatedPerson
            }
        }
    }


}
