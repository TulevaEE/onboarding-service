package ee.tuleva.onboarding.mandate.payment.rate

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.mandate.Mandate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult

import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class PaymentRateControllerSpec extends BaseControllerSpec {

  PaymentRateService paymentRateService = Mock()
  PaymentRateController controller = new PaymentRateController(paymentRateService)
  AuthenticatedPerson authenticatedPerson = AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember().build()
  MockMvc mvc = mockMvcWithAuthenticationPrincipal(authenticatedPerson, controller)

  def "update payment rate"() {
    when:
    BigDecimal newRate = new BigDecimal("6.0")
    PaymentRateCommand command = new PaymentRateCommand()
    command.setPaymentRate(newRate)

    Mandate aMandate = sampleMandate()
    paymentRateService.savePaymentRateMandate(authenticatedPerson, command.getPaymentRate()) >> aMandate

    then:
    MvcResult result = mvc
        .perform(post("/v1/second-pillar-payment-rates")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(command)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn()

    String jsonResponse = result.getResponse().getContentAsString()
    assert jsonResponse == '{"mandateId":123}'
  }

  def "update with invalid payment rate fails"() {
    when:
    BigDecimal newRate = new BigDecimal("3.0")
    PaymentRateCommand command = new PaymentRateCommand()
    command.setPaymentRate(newRate)

    Mandate aMandate = sampleMandate()
    paymentRateService.savePaymentRateMandate(authenticatedPerson, command.getPaymentRate()) >> aMandate

    then:
    mvc
        .perform(post("/v1/second-pillar-payment-rates")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(command)))
        .andExpect(status().isBadRequest())
        .andReturn()
  }

}
