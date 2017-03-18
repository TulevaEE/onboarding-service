package ee.tuleva.onboarding

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import ee.tuleva.onboarding.user.User
import org.springframework.core.MethodParameter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import spock.lang.Specification

import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class BaseControllerSpec extends Specification {

    protected final static ObjectMapper mapper = new ObjectMapper()

    protected MockMvc mockMvc(Object... controllers) {
        return standaloneSetup(controllers)
                .setMessageConverters(jacksonMessageConverter())
                .build()
    }

    protected MockMvc mockMvcWithAuthenticationPrincipal(User user, Object... controllers) {
        standaloneSetup(controllers)
                .setMessageConverters(jacksonMessageConverter())
                .setCustomArgumentResolvers(authenticationPrincipalResolver(user))
                .build()
    }

    private MappingJackson2HttpMessageConverter jacksonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter()
        converter.setObjectMapper(objectMapper)
        return converter
    }

    private HandlerMethodArgumentResolver authenticationPrincipalResolver(User user) {
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
