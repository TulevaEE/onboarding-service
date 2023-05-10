package ee.tuleva.onboarding.payment

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.payment.provider.PaymentProviderCallbackService
import ee.tuleva.onboarding.payment.provider.PaymentProviderService
import ee.tuleva.onboarding.payment.recurring.RecurringPaymentService
import org.springframework.http.MediaType

import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.payment.PaymentData.Bank.LHV
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SINGLE
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewPayment
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aSerializedPaymentProviderToken
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class PaymentControllerSpec extends BaseControllerSpec {

  PaymentService paymentService = Mock()

  PaymentController paymentController
  String frontendUrl = "https://frontend.url"

  def setup() {
    paymentController = new PaymentController(paymentService)
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
    def person = sampleAuthenticatedPerson
    def mvc = mockMvcWithAuthenticationPrincipal(person, paymentController)

    PaymentLink paymentLink = new PaymentLink("https://some.url?payment_token=23948h3t9gfd")

    PaymentData paymentData = PaymentData.builder()
        .amount(100.22)
        .currency(EUR)
        .type(SINGLE)
        .bank(LHV)
        .build()

    1 * paymentService.getLink(paymentData, person) >> paymentLink

    expect:
    mvc.perform(get("/v1/payments/link?amount=100.22&currency=EUR&type=SINGLE&bank=LHV"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.url', is(paymentLink.url())))
  }

  def "GET /success redirects to success screen on successful payment"() {
    given:
    def mvc = mockMvc(paymentController)
    1 * paymentService.processToken(aSerializedPaymentProviderToken) >> Optional.of(aNewPayment())
    expect:
    mvc.perform(get("/v1/payments/success")
        .param("payment_token", aSerializedPaymentProviderToken))
        .andExpect(redirectedUrl(frontendUrl + "/3rd-pillar-success"))
  }

  def "GET /success redirects back to payment screen on cancelled payment"() {
    given:
    def mvc = mockMvc(paymentController)
    1 * paymentService.processToken(aSerializedPaymentProviderToken) >> Optional.empty()
    expect:
    mvc.perform(get("/v1/payments/success")
        .param("payment_token", aSerializedPaymentProviderToken))
        .andExpect(redirectedUrl(frontendUrl + "/3rd-pillar-payment"))
  }

  def "POST /notifications"() {
    given:
    def mvc = mockMvc(paymentController)

    1 * paymentService.processToken(aSerializedPaymentProviderToken)
    expect:
    mvc.perform(post("/v1/payments/notifications")
        .param("payment_token", aSerializedPaymentProviderToken))
        .andExpect(status().isOk())
  }

  AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
      .firstName("Jordan")
      .lastName("Valdma")
      .personalCode("38501010000")
      .userId(2L)
      .build()

}
