package ee.tuleva.onboarding

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.error.ErrorHandlingControllerAdvice
import ee.tuleva.onboarding.error.response.ErrorResponseEntityFactory
import ee.tuleva.onboarding.error.response.InputErrorsConverter
import ee.tuleva.onboarding.user.User
import org.springframework.core.MethodParameter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import spock.lang.Specification

import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class BaseControllerSpec extends Specification {

    protected final static ObjectMapper mapper = new ObjectMapper()

    protected MockMvc mockMvc(Object... controllers) {
        getMockMvcWithControllerAdvice(controllers)
                .setCustomArgumentResolvers(authenticationPrincipalResolver(getDefaultAuthenticationPrincipal()))
                .build()
    }

    private getDefaultAuthenticationPrincipal() {
        return AuthenticatedPerson.builder()
                .user(
                    User.builder()
                        .active(true)
                        .build()
                ).build()
    }

    protected MockMvc mockMvcWithAuthenticationPrincipal(AuthenticatedPerson authenticatedPerson, Object... controllers) {
        getMockMvcWithControllerAdvice(controllers)
                .setCustomArgumentResolvers(authenticationPrincipalResolver(authenticatedPerson))
                .build()
    }

    private StandaloneMockMvcBuilder getMockMvcWithControllerAdvice(Object... controllers) {
        return standaloneSetup(controllers)
                .setMessageConverters(jacksonMessageConverter())
                .setControllerAdvice(errorHandlingControllerAdvice())
    }

    private ErrorHandlingControllerAdvice errorHandlingControllerAdvice() {
        ErrorHandlingControllerAdvice controllerAdvice =
                new ErrorHandlingControllerAdvice(new ErrorResponseEntityFactory(new InputErrorsConverter()));

        return controllerAdvice;
    }

    private MappingJackson2HttpMessageConverter jacksonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter()
        converter.setObjectMapper(objectMapper)
        return converter
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
