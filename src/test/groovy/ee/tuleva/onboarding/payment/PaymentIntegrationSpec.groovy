package ee.tuleva.onboarding.payment

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.epis.account.FundBalanceDto
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus
import ee.tuleva.onboarding.event.EventLogRepository
import ee.tuleva.onboarding.mandate.application.PaymentLinkingService
import ee.tuleva.onboarding.payment.provider.PaymentProviderFixture
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
import org.springframework.test.context.TestPropertySource
import org.springframework.web.servlet.view.RedirectView
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.epis.cashflows.CashFlowFixture.cashFlowFixture
import static ee.tuleva.onboarding.epis.cashflows.CashFlowFixture.cashFlowStatementFor3rdPillarPayment
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.application.PaymentLinkingService.TULEVA_3RD_PILLAR_FUND_ISIN
import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentAmount
import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentData
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aSerializedCallbackFinalizedToken
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.getAnInternalReference
import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.MediaType.APPLICATION_JSON

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@MockServerTest("epis.service.url=http://localhost:\${mockServerPort}")
@Import(Config.class)
@TestPropertySource(properties = "PAYMENT_SECRET_LHV=exampleSecretKeyexampleSecretKeyexampleSecretKey")
@TestPropertySource(properties = "payment-provider.banks.lhv.access-key=exampleAccessKey")
class PaymentIntegrationSpec extends Specification {

  static class Config {
    @Bean
    @ConditionalOnMissingBean(value = ErrorAttributes.class)
    DefaultErrorAttributes errorAttributes() {
      return new DefaultErrorAttributes()
    }
  }

  @Autowired PaymentController paymentController
  @Autowired UserRepository userRepository
  @Autowired PaymentRepository paymentRepository
  @Autowired EventLogRepository eventLogRepository

  @Autowired
  PaymentLinkingService paymentApplicationService

  @Value('${frontend.url}')
  String frontendUrl

  MockServerClient mockServerClient

  ObjectMapper mapper = JsonMapper.builder()
      .addModule(new JavaTimeModule())
      .build()

  String aToken = "token-string"

  def setup() {
    mockSecurityContext()
  }

  def mockSecurityContext() {
    SecurityContext sc = SecurityContextHolder.createEmptyContext()
    TestingAuthenticationToken authentication = new TestingAuthenticationToken("test", aToken)
    sc.authentication = authentication
    SecurityContextHolder.context = sc
  }

  def cleanup() {
    SecurityContextHolder.clearContext()
    paymentRepository.deleteAll()
    eventLogRepository.deleteAll()
    userRepository.deleteAll()
  }

  def "Payment happy flow"() {
    given:

    User aUser = userRepository.save(sampleUserNonMember().id(null).build())
    AuthenticatedPerson anAuthenticatedPerson = authenticatedPersonFromUser(aUser).build()

    mockEpisContactDetails()
    mockEpisTransactions()
    mockEpisApplications()
    mockEpisAccountStatement()

    expect:
    expectAPaymentLink(anAuthenticatedPerson)
    expectNoPaymentsStored()
    SecurityContextHolder.clearContext()
    expectThatPaymentCallbackRedirectsUser()
    expectThatPaymentCallbackCreatedOnePayment(aUser)
    mockEpisTransactionsForPayment()
    expectToBeAbleToReceivePaymentNotification()
    mockSecurityContext()
    expectLinkedPaymentAndTransactions(anAuthenticatedPerson)
  }

  private void expectLinkedPaymentAndTransactions(AuthenticatedPerson anAuthenticatedPerson) {
    def applications = paymentApplicationService
        .getPaymentApplications(anAuthenticatedPerson)
    def application = applications.first()
    assert applications.size() == 1
    assert application.status == ApplicationStatus.PENDING
    assert application.details.amount == aPaymentAmount
    assert application.details.currency == Currency.EUR
    assert application.details.targetFund.pillar == 3
    assert application.details.targetFund.isin == TULEVA_3RD_PILLAR_FUND_ISIN
  }

  private void expectNoPaymentsStored() {
    assert paymentRepository.findAll().isEmpty()
  }

  private void expectToBeAbleToReceivePaymentNotification() {
    paymentController.paymentCallback(aSerializedCallbackFinalizedToken)
    assert paymentRepository.findAll().size() == 1
  }

  private void expectThatPaymentCallbackCreatedOnePayment(User aUser) {
    assert paymentRepository.findAll().size() == 1
    def payment = paymentRepository.findAll().asList().first()
    assert payment.internalReference == anInternalReference.uuid
    assert payment.amount == aPaymentAmount
    assert payment.user.id == aUser.id
  }

  private void expectThatPaymentCallbackRedirectsUser() {
    RedirectView result = paymentController.getPaymentSuccessRedirect(aSerializedCallbackFinalizedToken)
    assert result.url == frontendUrl + "/3rd-pillar-success"
  }

  private void expectAPaymentLink(AuthenticatedPerson anAuthenticatedPerson) {
    PaymentLink paymentLink = paymentController.getPaymentLink(aPaymentData, anAuthenticatedPerson)
    assert paymentLink.url().startsWith('https://')
  }

  private void mockEpisTransactionsForPayment() {
    mockServerClient
        .when(request("/account-cash-flow-statement")
            .withHeader("Authorization", "Bearer $aToken"))
        .respond(response()
            .withContentType(APPLICATION_JSON)
            .withBody((mapper.writeValueAsString(cashFlowStatementFor3rdPillarPayment(
                paymentRepository.findAll().asList().first()
            ))))
        )
  }

  private void mockEpisTransactions() {
    mockServerClient
        .when(request("/account-cash-flow-statement")
            .withHeader("Authorization", "Bearer $aToken"))
        .respond(response()
            .withContentType(APPLICATION_JSON)
            .withBody((mapper.writeValueAsString(cashFlowFixture())))
        )
  }

  private void mockEpisApplications() {
    mockServerClient
        .when(request("/applications")
            .withHeader("Authorization", "Bearer $aToken"))
        .respond(response()
            .withContentType(APPLICATION_JSON)
            .withBody((mapper.writeValueAsString(List.of())))
        )
  }

  private void mockEpisContactDetails() {
    mockServerClient
        .when(request("/contact-details")
            .withHeader("Authorization", "Bearer $aToken"))
        .respond(response()
            .withContentType(APPLICATION_JSON)
            .withBody((mapper.writeValueAsString(contactDetailsFixture())))
        )
  }

  private void mockEpisAccountStatement() {
    mockServerClient
        .when(request("/account-statement")
            .withHeader("Authorization", "Bearer $aToken"))
        .respond(response()
            .withContentType(APPLICATION_JSON)
            .withBody((mapper.writeValueAsString(
                List.of(FundBalanceDto.builder().isin(TULEVA_3RD_PILLAR_FUND_ISIN).build())
            )))
        )
  }
}
