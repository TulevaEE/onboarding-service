package ee.tuleva.onboarding.payment.provider

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.currency.Currency
import org.springframework.http.MediaType

import static ee.tuleva.onboarding.payment.PaymentFixture.aNewPayment
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.*
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class PaymentControllerSpec extends BaseControllerSpec {

  PaymentProviderService paymentProviderService = Mock()
    PaymentProviderCallbackService paymentProviderCallbackService = Mock()

    PaymentController paymentController
  String frontendUrl = "https://frontend.url"

  def setup() {
    paymentController = new PaymentController(
        paymentProviderService,
        paymentProviderCallbackService
    )
    paymentController.frontendUrl = frontendUrl
  }

  def "GET /payments/link fails with other currencies than EUR"() {
    given:
    def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, paymentController)

    expect:
    mvc.perform(get("/v1/payments/link?amount=100&currency=USD&bank=LHV"))
        .andExpect(status().isBadRequest())
  }

  def "GET /payments/link"() {
    given:
    def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, paymentController)

    PaymentLink paymentLink = new PaymentLink("https://some.url?payment_token=23948h3t9gfd")

    PaymentData paymentData = PaymentData.builder()
      .person(sampleAuthenticatedPerson)
      .currency(Currency.EUR)
      .amount(100.22)
      .bank(Bank.LHV)
      .build()

    1 * paymentProviderService.getPaymentLink(paymentData) >> paymentLink

    expect:
    mvc.perform(get("/v1/payments/link?amount=100.22&currency=EUR&bank=LHV"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.url', is(paymentLink.url())))
  }

  def "GET /success redirects to success screen on successful payment"() {
    given:
    def mvc = mockMvc(paymentController)
    1 * paymentProviderCallbackService.processToken(aSerializedToken) >> Optional.of(aNewPayment())
    expect:
    mvc.perform(get("/v1/payments/success")
        .param("payment_token", aSerializedToken))
        .andExpect(redirectedUrl(frontendUrl + "/3rd-pillar-flow/success"))
  }

  def "GET /success redirects back to payment screen on cancelled payment"() {
    given:
    def mvc = mockMvc(paymentController)
    1 * paymentProviderCallbackService.processToken(aSerializedToken) >> Optional.empty()
    expect:
    mvc.perform(get("/v1/payments/success")
        .param("payment_token", aSerializedToken))
        .andExpect(redirectedUrl(frontendUrl + "/3rd-pillar-flow/payment"))
  }

  def "POST /notifications"() {
    given:
    def mvc = mockMvc(paymentController)

    1 * paymentProviderCallbackService.processToken(aSerializedToken)
    expect:
    mvc.perform(post("/v1/payments/notifications")
        .param("payment_token", aSerializedToken))
        .andExpect(status().isOk())
  }

  AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
      .firstName("Jordan")
      .lastName("Valdma")
      .personalCode("38501010000")
      .userId(2L)
      .build()

}
