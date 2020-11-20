package ee.tuleva.onboarding.error

import ee.tuleva.onboarding.account.AccountStatementService
import ee.tuleva.onboarding.account.CashFlowService
import ee.tuleva.onboarding.config.OAuthConfiguration
import ee.tuleva.onboarding.config.SecurityConfiguration
import ee.tuleva.onboarding.error.converter.ErrorAttributesConverter
import ee.tuleva.onboarding.error.converter.InputErrorsConverter
import ee.tuleva.onboarding.error.response.ErrorResponseEntityFactory
import ee.tuleva.onboarding.fund.FundRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes
import org.springframework.boot.web.servlet.error.ErrorAttributes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import javax.servlet.RequestDispatcher

import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

@WebMvcTest(ErrorHandlingController)
@WithMockUser
@Import([ErrorResponseEntityFactory, InputErrorsConverter, ErrorAttributesConverter,
    OAuthConfiguration.ResourceServerPathConfiguration, SecurityConfiguration])
class ErrorHandlingControllerSpec extends Specification {

    @MockBean
    CashFlowService cashFlowService

    @TestConfiguration
    static class ErrorAttributesConfiguration {
        @Bean
        ErrorAttributes defaultErrorAttributes() {
            return new DefaultErrorAttributes(true)
        }
    }

    @Autowired
    MockMvc mvc

    @MockBean
    FundRepository fundRepository

    @MockBean
    AccountStatementService accountStatementService

    def "error handling works"() {
        expect:
        mvc.perform(get("/error")
            .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new RuntimeException())
            .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
            .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/asdf")
            .requestAttr(RequestDispatcher.ERROR_MESSAGE, "oops!"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath('$.errors[0].code', is("RuntimeException")))
            .andExpect(jsonPath('$.errors[0].message', is("oops!")))
            .andExpect(jsonPath('$.errors[0].path').doesNotExist())
            .andExpect(jsonPath('$.errors[0].arguments').doesNotExist())
    }

}
