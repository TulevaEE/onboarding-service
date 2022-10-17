package ee.tuleva.onboarding.error

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import ee.tuleva.onboarding.mandate.*
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.servlet.LocaleResolver

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ErrorHandlingControllerAdviceSpec extends BaseControllerSpec {

  MandateRepository mandateRepository = Mock()
  MandateService mandateService = Mock()
  GenericSessionStore sessionStore = Mock()
  SignatureFileArchiver signatureFileArchiver = Mock()
  MandateFileService mandateFileService = Mock()
  LocaleResolver localeResolver = Mock()

  MandateController controller =
      new MandateController(mandateRepository, mandateService, sessionStore, signatureFileArchiver, mandateFileService, localeResolver)

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
        .andExpect(content().json('''
        {"errors":[
          {"code":"NotNull","message":"must not be null","path":"fundTransferExchanges","arguments":[]},
          {"code":"NotNull","message":"must not be null","path":"address","arguments":[]}
        ]}
      '''))
  }

}
