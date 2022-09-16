package ee.tuleva.onboarding.payment

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.epis.EpisService
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class PaymentControllerSpec extends BaseControllerSpec {

  PaymentProviderService paymentProviderService = Mock()

  PaymentController paymentController
  String frontendUrl = "https://frontend.url"

  def setup() {
    paymentController = new PaymentController(
        paymentProviderService,
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

    String paymentUrl = "https://some.url?payment_token=23948h3t9gfd"

    PaymentData paymentData = PaymentData.builder()
      .person(sampleAuthenticatedPerson)
        .currency(Currency.EUR)
        .amount(BigDecimal.valueOf(100))
        .bank(Bank.LHV)
        .build()

    1 * paymentProviderService.getPaymentUrl(paymentData) >> paymentUrl

    expect:
    mvc.perform(get("/v1/payments/link?amount=100&currency=EUR&bank=LHV"))
        .andExpect(redirectedUrl(paymentUrl))
  }

  def "GET /success"() {
    given:
    def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, paymentController)

    expect:
    mvc.perform(get("/v1/payments/success"))
        .andExpect(redirectedUrl(frontendUrl + "/3rd-pillar-flow/success/"))
  }

  def "POST /notifications"() {
    given:
    def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, paymentController)

    expect:
    mvc.perform(post("/v1/payments/notifications?payment_token=asdf1234"))
        .andExpect(status().isOk())
  }

  AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
      .firstName("Jordan")
      .lastName("Valdma")
      .personalCode("38501010000")
      .userId(2L)
      .build()

}
