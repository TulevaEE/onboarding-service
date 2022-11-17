package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.account.AccountStatementService
import ee.tuleva.onboarding.account.CashFlowService
import ee.tuleva.onboarding.auth.OAuth2Fixture
import ee.tuleva.onboarding.auth.PersonalCodeAuthenticationProvider
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.contact.ContactDetailsService
import ee.tuleva.onboarding.error.converter.ErrorAttributesConverter
import ee.tuleva.onboarding.error.converter.InputErrorsConverter
import ee.tuleva.onboarding.error.response.ErrorResponseEntityFactory
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator
import ee.tuleva.onboarding.test.ControllerTest
import ee.tuleva.onboarding.test.WithPersonalCodeUser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

@ControllerTest(controllers = UserController)
@WithPersonalCodeUser
@MockBean(value = [UserService, EpisService, ContactDetailsService])
class UserControllerIntSpec extends Specification {
  @Autowired
  MockMvc mvc

  def "Gets principal from security context"() {
    expect:
    mvc.perform(get("/v1/me/principal"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.personalCode', is("38812121215")))
  }
}
