package ee.tuleva.onboarding.payment

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.savings.fund.SavingFundPayment
import ee.tuleva.onboarding.user.User
import org.springframework.http.MediaType

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LHV
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.PARTNER
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.*
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewMemberPayment
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewSinglePayment
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aSerializedSavingsPaymentToken
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aSerializedSinglePaymentFinishedToken
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.montonioNotification
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class PaymentControllerSpec extends BaseControllerSpec {

  ObjectMapper objectMapper = new ObjectMapper()

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
    mvc.perform(get("/v1/payments/link?amount=100&currency=USD&paymentChannel=LHV&recipientPersonalCode=37605030299"))
        .andExpect(status().isBadRequest())
  }

  def "GET /payments/link fails with invalid recipient personal code"() {
    given:
    def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, paymentController)

    expect:
    mvc.perform(get("/v1/payments/link?amount=100&currency=USD&paymentChannel=LHV&recipientPersonalCode=37605030290"))
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
        .paymentChannel(LHV)
        .recipientPersonalCode("37605030299")
        .build()

    1 * paymentService.getLink(paymentData, person) >> paymentLink

    expect:
    mvc.perform(get("/v1/payments/link?amount=100.22&currency=EUR&type=SINGLE&paymentChannel=LHV&recipientPersonalCode=37605030299"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.url', is(paymentLink.url())))
  }

  def "GET /payments/link works with no amount and currency"() {
    given:
    def person = sampleAuthenticatedPerson
    def mvc = mockMvcWithAuthenticationPrincipal(person, paymentController)

    PaymentLink paymentLink = new PaymentLink("https://some.url?payment_token=23948h3t9gfd")

    PaymentData paymentData = PaymentData.builder()
        .type(RECURRING)
        .paymentChannel(PARTNER)
        .recipientPersonalCode("37605030299")
        .build()

    1 * paymentService.getLink(paymentData, person) >> paymentLink

    expect:
    mvc.perform(get("/v1/payments/link?type=RECURRING&paymentChannel=PARTNER&recipientPersonalCode=37605030299"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.url', is(paymentLink.url())))
  }

  def "GET /success redirects to success screen on successful payment"() {
    given:
    def mvc = mockMvc(paymentController)
    1 * paymentService.processToken(aSerializedSinglePaymentFinishedToken) >> Optional.of(aNewSinglePayment())
    expect:
    mvc.perform(get("/v1/payments/success")
        .param("order-token", aSerializedSinglePaymentFinishedToken))
        .andExpect(redirectedUrl(frontendUrl + "/3rd-pillar-success"))
  }

  def "GET /success redirects back to payment screen on cancelled payment"() {
    given:
    def mvc = mockMvc(paymentController)
    1 * paymentService.processToken(aSerializedSinglePaymentFinishedToken) >> Optional.empty()
    expect:
    mvc.perform(get("/v1/payments/success")
        .param("order-token", aSerializedSinglePaymentFinishedToken))
        .andExpect(redirectedUrl(frontendUrl + "/3rd-pillar-payment"))
  }

  def "GET /member-success redirects to membership success screen on MEMBER_FEE payment"() {
    given:
    def mvc = mockMvc(paymentController)
    Payment payment = aNewMemberPayment()
    payment.setPaymentType(MEMBER_FEE)
    User user = sampleUserNonMember().build()

    payment.setUser(user)

    1 * paymentService.processToken(aSerializedSinglePaymentFinishedToken) >> Optional.of(payment)

    expect:
    mvc.perform(get("/v1/payments/member-success")
        .param("order-token", aSerializedSinglePaymentFinishedToken))
        .andExpect(redirectedUrl(frontendUrl))
  }

  def "GET /member-success redirects back to account page on cancelled payment"() {
    given:
    def mvc = mockMvc(paymentController)
    1 * paymentService.processToken(aSerializedSinglePaymentFinishedToken) >> Optional.empty()
    expect:
    mvc.perform(get("/v1/payments/member-success")
        .param("order-token", aSerializedSinglePaymentFinishedToken))
        .andExpect(redirectedUrl(frontendUrl + "/account"))
  }

  def "POST /notifications"() {
    given:
    def mvc = mockMvc(paymentController)

    1 * paymentService.processToken(aSerializedSinglePaymentFinishedToken)
    expect:
    mvc.perform(post("/v1/payments/notifications")
        .content(objectMapper.writeValueAsString(montonioNotification))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
  }

  def "GET /savings/callback redirects to success"() {
    given:
    def mvc = mockMvc(paymentController)

    1 * paymentService.processSavingsPaymentToken(aSerializedSavingsPaymentToken) >> Optional.of(SavingFundPayment.builder().build())
    expect:
    mvc.perform(get("/v1/payments/savings/callback")
        .param("order-token", aSerializedSavingsPaymentToken))
        .andExpect(redirectedUrl(frontendUrl + "/savings-fund/payment/success"))
  }

  def "GET /savings/callback redirects back to payment on error"() {
    given:
    def mvc = mockMvc(paymentController)

    1 * paymentService.processSavingsPaymentToken(aSerializedSavingsPaymentToken) >> Optional.empty()
    expect:
    mvc.perform(get("/v1/payments/savings/callback")
        .param("order-token", aSerializedSavingsPaymentToken))
        .andExpect(redirectedUrl(frontendUrl + "/savings-fund/payment"))
  }

  def "POST /savings/notifications"() {
    given:
    def mvc = mockMvc(paymentController)

    1 * paymentService.processSavingsPaymentToken(aSerializedSinglePaymentFinishedToken)
    expect:
    mvc.perform(post("/v1/payments/savings/notifications")
        .content(objectMapper.writeValueAsString(montonioNotification))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
  }

  AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
      .firstName("Jordan")
      .lastName("Valdma")
      .personalCode("38501010000")
      .userId(2L)
      .build()

}
