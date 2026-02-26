package ee.tuleva.onboarding

import tools.jackson.core.StreamWriteFeature
import tools.jackson.databind.json.JsonMapper
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.error.ErrorHandlingControllerAdvice
import ee.tuleva.onboarding.error.converter.InputErrorsConverter
import ee.tuleva.onboarding.error.response.ErrorResponseEntityFactory
import org.springframework.core.MethodParameter
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import spock.lang.Specification

import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

/*
Deprecated since
seems that BaseControllerSpecs do not test serialization and other things like it runs in actually.
For example serialization for generic MandateDetails that works here can fail in integration tests.
Use @WebMvcTest instead
*/

@Deprecated(since = "Use @WebMvcTest instead")
class BaseControllerSpec extends Specification {

    protected final static JsonMapper mapper = JsonMapper.builder().build()

    protected MockMvc mockMvc(Object... controllers) {
        getMockMvcWithControllerAdvice(controllers)
                .setCustomArgumentResolvers(authenticationPrincipalResolver(getDefaultAuthenticationPrincipal()))
                .build()
    }

    private getDefaultAuthenticationPrincipal() {
        return AuthenticatedPerson.builder()
                .userId(1L)
                .build()
    }

    protected MockMvc mockMvcWithAuthenticationPrincipal(AuthenticatedPerson authenticatedPerson, Object... controllers) {
        getMockMvcWithControllerAdvice(controllers)
                .setCustomArgumentResolvers(authenticationPrincipalResolver(authenticatedPerson))
                .build()
    }

    private StandaloneMockMvcBuilder getMockMvcWithControllerAdvice(Object... controllers) {
        return standaloneSetup(controllers)
                .setMessageConverters(jacksonMessageConverter())
                .setControllerAdvice(new ErrorHandlingControllerAdvice())
    }

  private JacksonJsonHttpMessageConverter jacksonMessageConverter() {
        JsonMapper objectMapper = JsonMapper.builder()
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build()
        return new JacksonJsonHttpMessageConverter(objectMapper)
    }

    private HandlerMethodArgumentResolver authenticationPrincipalResolver(AuthenticatedPerson authenticatedPerson) {
        return new HandlerMethodArgumentResolver() {
            @Override
            boolean supportsParameter(MethodParameter parameter) {
                return parameter.parameterType == AuthenticatedPerson
            }

            @Override
            Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
                return authenticatedPerson
            }
        }
    }

}
