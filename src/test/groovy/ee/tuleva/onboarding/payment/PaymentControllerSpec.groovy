package ee.tuleva.onboarding.payment

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import org.junit.jupiter.api.BeforeAll
import org.springframework.http.MediaType

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class PaymentControllerSpec extends BaseControllerSpec {

  PaymentController paymentController = new PaymentController()
  String frontendUrl = "https://frontend.url"
  @BeforeAll
  def setup() {
    paymentController.frontendUrl = frontendUrl
  }

  def "POST /payment fails with other currencies than EUR"() {
    given:
    def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, paymentController)

    expect:
    mvc.perform(post("/v1/payment")
        .content("""{"amount": 100, "currency": "USD" }""")
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
  }

  def "POST /payment"() {
    given:
    def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, paymentController)

    expect:
    mvc.perform(post("/v1/payment")
        .content("""{"amount": 100, "currency": "EUR" }""")
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
  }

  def "GET /success"() {
    given:
    def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, paymentController)

    expect:
    mvc.perform(get("/v1/payment/success"))
        .andExpect(redirectedUrl(frontendUrl + "/3rd-pillar-flow/success/"))
  }

  AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
      .firstName("Jordan")
      .lastName("Valdma")
      .personalCode("38501010000")
      .userId(2L)
      .build()

}
