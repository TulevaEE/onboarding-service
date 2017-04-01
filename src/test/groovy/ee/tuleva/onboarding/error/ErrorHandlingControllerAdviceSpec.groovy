package ee.tuleva.onboarding.error

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import ee.tuleva.onboarding.mandate.*
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ErrorHandlingControllerAdviceSpec extends BaseControllerSpec {

    MandateRepository mandateRepository = Mock(MandateRepository)
    MandateService mandateService = Mock(MandateService)
    GenericSessionStore sessionStore = Mock(GenericSessionStore)
    SignatureFileArchiver signatureFileArchiver = Mock(SignatureFileArchiver)
    MandateFileService mandateFileService = Mock(MandateFileService)

    MandateController controller =
            new MandateController(mandateRepository, mandateService, sessionStore, signatureFileArchiver, mandateFileService)

    MockMvc mvc = mockMvc(controller)

    def "handleErrors: responds to errors correctly"() {
        CreateMandateCommand invalidCreateMandateCommand = [:]

        expect:
        mvc
                .perform(post("/v1/mandates/").content(
                mapper.writeValueAsString(
                        invalidCreateMandateCommand
                ))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().json('{"errors":[{"code":"NotNull","message":"may not be null","path":"fundTransferExchanges","arguments":[]}]}'))
    }

}
