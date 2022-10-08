package ee.tuleva.onboarding.payment

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus
import ee.tuleva.onboarding.mandate.application.PaymentApplicationService
import ee.tuleva.onboarding.payment.provider.PaymentController
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserRepository
import org.mockserver.client.MockServerClient
import org.mockserver.springtest.MockServerTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes
import org.springframework.boot.web.servlet.error.ErrorAttributes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails
import org.springframework.test.context.TestPropertySource

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.epis.cashflows.CashFlowFixture.cashFlowFixtureThatMatchesPayment
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aSerializedCallbackFinalizedTokenWithCorrectIdCode
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.anInternalReference
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aPaymentAmount
import static ee.tuleva.onboarding.mandate.application.PaymentApplicationService.TULEVA_3RD_PILLAR_FUND_ISIN
import static org.hamcrest.Matchers.startsWith
import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.MediaType.APPLICATION_JSON
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@MockServerTest("epis.service.url=http://localhost:\${mockServerPort}")
@TestPropertySource(properties = "spring.cache.type=ehcache")
@Import(Config.class)
@TestPropertySource(properties = "PAYMENT_SECRET_LHV=exampleSecretKeyexampleSecretKeyexampleSecretKey")
@TestPropertySource(properties = "payment-provider.banks.lhv.access-key=exampleAccessKey")
class PaymentIntegrationSpec extends BaseControllerSpec {

  static class Config {
    @Bean
    @ConditionalOnMissingBean(value = ErrorAttributes.class)
    DefaultErrorAttributes errorAttributes() {
      return new DefaultErrorAttributes()
    }
  }

  @Autowired
  PaymentController paymentController
  @Autowired
  UserRepository userRepository
  @Autowired
  PaymentRepository paymentRepository

  @Autowired
  PaymentApplicationService paymentApplicationService

  @Value('${frontend.url}')
  private String frontendUrl

  private MockServerClient mockServerClient

  AuthenticatedPerson anAuthenticatedPerson
  User aUser
  def setup() {
    SecurityContext sc = SecurityContextHolder.createEmptyContext()
    TestingAuthenticationToken authentication = new TestingAuthenticationToken("test", "password")
    OAuth2AuthenticationDetails details = Mock(OAuth2AuthenticationDetails)
    authentication.details = details
    details.getTokenValue() >> "dummy"
    sc.authentication = authentication
    SecurityContextHolder.context = sc
    aUser = userRepository.save(sampleUserNonMember().id(null).build())
    anAuthenticatedPerson = AuthenticatedPerson.builder()
        .firstName("Jordan")
        .lastName("Valdma")
        .personalCode(aUser.getPersonalCode())
        .userId(aUser.id)
        .build()
  }

  def cleanup() {
    SecurityContextHolder.clearContext()
  }


  def "GET /payments/link"() {
    given:

    mockServerClient
        .when(request("/contact-details")
            .withHeader("Authorization", "Bearer dummy"))
        .respond(response()
            .withContentType(APPLICATION_JSON)
            .withBody(((new ObjectMapper()).writeValueAsString(contactDetailsFixture())))
        )

    ObjectMapper mapper = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .build()

    mockServerClient
        .when(request("/account-cash-flow-statement")
            .withHeader("Authorization", "Bearer dummy"))
        .respond(response()
            .withContentType(APPLICATION_JSON)
            .withBody((mapper.writeValueAsString(cashFlowFixtureThatMatchesPayment())))
        )

    def mvc = mockMvcWithAuthenticationPrincipal(anAuthenticatedPerson, paymentController)

    expect:
    mvc.perform(get("/v1/payments/link?amount=100.22&currency=EUR&bank=LHV"))
        .andExpect(status().isOk())
    .andExpect(jsonPath('$.url', startsWith('https://')))
    .andReturn()

    paymentRepository.findAll().isEmpty()

    mvc.perform(get("/v1/payments/success")
        .param("payment_token", aSerializedCallbackFinalizedTokenWithCorrectIdCode))
        .andExpect(redirectedUrl(frontendUrl + "/3rd-pillar-flow/success"))

    paymentRepository.findAll().size() == 1
    paymentRepository.findAll().asList().first().status == PaymentStatus.PENDING

    mvc.perform(post("/v1/payments/notifications")
        .param("payment_token", aSerializedCallbackFinalizedTokenWithCorrectIdCode))
        .andExpect(status().isOk())

    paymentRepository.findAll().size() == 1
    def payment = paymentRepository.findAll().asList().first()
    payment.status == PaymentStatus.PENDING
    payment.internalReference.equals(anInternalReference.uuid)
    payment.amount == aPaymentAmount
    payment.user.id == aUser.id

    def applications = paymentApplicationService.getPaymentApplications(anAuthenticatedPerson)
    applications.size() == 1
    def application = applications.first()
    application.status == ApplicationStatus.COMPLETE
    application.details.amount == aPaymentAmount
    application.details.currency == Currency.EUR
    application.details.targetFund.pillar == 3
    application.details.targetFund.isin == TULEVA_3RD_PILLAR_FUND_ISIN
  }
}
