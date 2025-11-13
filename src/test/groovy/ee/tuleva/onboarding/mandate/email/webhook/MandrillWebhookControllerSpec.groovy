package ee.tuleva.onboarding.mandate.email.webhook


import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil
import ee.tuleva.onboarding.auth.principal.PrincipalService
import ee.tuleva.onboarding.config.SecurityConfiguration
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(MandrillWebhookController)
@Import(SecurityConfiguration)
class MandrillWebhookControllerSpec extends Specification {

  @Autowired
  MockMvc mvc

  @SpringBean
  MandrillWebhookService webhookService = Mock()

  @SpringBean
  JwtTokenUtil jwtTokenUtil = Mock()

  @SpringBean
  PrincipalService principalService = Mock()

  def "responds to HEAD request for webhook verification"() {
    when:
    def result = mvc.perform(head("/v1/emails/webhooks/mandrill"))

    then:
    result.andExpect(status().isOk())
  }

  def "delegates to webhook service"() {
    given:
    def eventsJson = "[]"

    when:
    def result = mvc.perform(post("/v1/emails/webhooks/mandrill")
        .param("mandrill_events", eventsJson)
        .header("X-Mandrill-Signature", "valid_signature")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED))

    then:
    result.andExpect(status().isOk())
    1 * webhookService.handleWebhook(eventsJson, "valid_signature", _)
  }
}
