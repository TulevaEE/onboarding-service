package ee.tuleva.onboarding.payment

import tools.jackson.databind.json.JsonMapper
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.role.Role
import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.epis.account.FundBalanceDto
import ee.tuleva.onboarding.event.EventLogRepository
import ee.tuleva.onboarding.mandate.application.ApplicationService
import ee.tuleva.onboarding.mandate.application.ApplicationStatus
import ee.tuleva.onboarding.payment.application.PaymentLinkingService
import ee.tuleva.onboarding.payment.provider.montonio.MontonioApiClient
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserRepository
import org.mockserver.client.MockServerClient
import org.mockserver.model.Header
import org.mockserver.springtest.MockServerTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.view.RedirectView
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON
import static ee.tuleva.onboarding.applicationtype.ApplicationType.PAYMENT
import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.PENDING
import static ee.tuleva.onboarding.epis.CashFlowFixture.cashFlowFixture
import static ee.tuleva.onboarding.epis.CashFlowFixture.cashFlowStatementFor3rdPillarPayment
import static ee.tuleva.onboarding.epis.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentAmount
import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentData
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.GIFT
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SINGLE
import static ee.tuleva.onboarding.payment.application.PaymentLinkingService.TULEVA_3RD_PILLAR_FUND_ISIN
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.*
import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.MediaType.APPLICATION_JSON

@SpringBootTest
@MockServerTest(["epis.service.url=http://localhost:\${mockServerPort}", "payment-provider.url=http://localhost:\${mockServerPort}/api"])
@Transactional
class PaymentIntegrationSpec extends Specification {

  @Autowired
  PaymentController paymentController
  @Autowired
  UserRepository userRepository
  @Autowired
  PaymentRepository paymentRepository
  @Autowired
  EventLogRepository eventLogRepository

  @Autowired
  JsonMapper objectMapper

  @Autowired
  PaymentLinkingService paymentApplicationService

  @Autowired
  ApplicationService applicationService

  @Value('${frontend.url}')
  String frontendUrl

  @Value('${payment-provider.url}')
  String montonioUrl

  MockServerClient mockServerClient

  JsonMapper mapper = JsonMapper.builder().build()

  String aToken = "token-string"
  User aUser

  def setup() {
    mockSecurityContext()
    aUser = userRepository.save(sampleUserNonMember().id(null).build())
  }

  def mockMontonioOrderApi() {
    URI uri = new URI(montonioUrl)
    String host = uri.getHost()

    String responseBody = objectMapper.writeValueAsString(new MontonioApiClient.MontonioOrderResponse("https://payment.test"))

    new MockServerClient(host, uri.getPort())
        .when(
            request()
                .withMethod("POST")
                .withPath("/api/orders")
                .withHeader(new Header("Content-Type", "application/json"))
        )
        .respond(
            response()
                .withStatusCode(200)
                .withHeader(
                    new Header("Content-Type", "application/json; charset=utf-8")
                )
                .withBody(responseBody)
        )
  }

  def mockSecurityContext() {
    SecurityContext sc = SecurityContextHolder.createEmptyContext()
    TestingAuthenticationToken authentication = new TestingAuthenticationToken("test", aToken)
    sc.authentication = authentication
    SecurityContextHolder.context = sc
  }

  def cleanup() {
    SecurityContextHolder.clearContext()
  }

  def "Payment happy flow"() {
    given:
    AuthenticatedPerson anAuthenticatedPerson = authenticatedPersonFromUser(aUser).build()

    mockEpisContactDetails()
    mockEpisContributions()
    mockEpisTransactions()
    mockEpisApplications()
    mockEpisAccountStatement()
    mockMontonioOrderApi()

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

  def "a third pillar payment shows only in its recipient's role: the payer's own payment for the payer, a gift to the child for the child, nothing for a company"() {
    given:
    def childPersonalCode = "61506150006"
    def payer = authenticatedPersonFromUser(aUser).build()
    def payerActingForChild = authenticatedPersonFromUser(aUser)
        .role(new Role(PERSON, childPersonalCode, "Child Name"))
        .build()
    def payerActingForCompany = authenticatedPersonFromUser(aUser)
        .role(new Role(LEGAL_ENTITY, "12345678", "Acme OÜ"))
        .build()

    def ownPayment = aStoredThirdPillarPayment(aUser.personalCode, aPaymentAmount, SINGLE,
        "3ab94f11-fb71-4401-8043-5e911227037e")
    def giftToChild = aStoredThirdPillarPayment(childPersonalCode, 25.00, GIFT,
        "7d3c2b1a-4e5f-4a6b-9c8d-0e1f2a3b4c5d")
    mockEpisApplications()
    mockEpisTransactions()

    expect:
    pendingPayments(payer) == [[PAYMENT, ownPayment.id, aPaymentAmount]]
    pendingPayments(payerActingForChild) == [[PAYMENT, giftToChild.id, 25.00]]
    applicationService.getApplications(PENDING, payerActingForCompany).isEmpty()
  }

  private Payment aStoredThirdPillarPayment(String recipientPersonalCode, BigDecimal amount,
                                            PaymentData.PaymentType paymentType, String internalReference) {
    return paymentRepository.save(Payment.builder()
        .user(aUser)
        .internalReference(UUID.fromString(internalReference))
        .amount(amount)
        .recipientPersonalCode(recipientPersonalCode)
        .paymentType(paymentType)
        .build())
  }

  private List<List<Object>> pendingPayments(AuthenticatedPerson person) {
    return applicationService.getApplications(PENDING, person)
        .collect { [it.type, it.id, it.details.amount] }
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
    paymentController.paymentCallback(montonioNotification)
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
    // TODO use mock mvc to test this instead of directly calling the controller
    RedirectView result = paymentController.getThirdPillarPaymentSuccessRedirect(aSerializedSinglePaymentFinishedToken)
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
        .when(request("/account-cash-flow-statement"))
        .respond(response()
            .withContentType(APPLICATION_JSON)
            .withBody((mapper.writeValueAsString(cashFlowFixture())))
        )
  }

  private void mockEpisApplications() {
    mockServerClient
        .when(request("/applications"))
        .respond(response()
            .withContentType(APPLICATION_JSON)
            .withBody((mapper.writeValueAsString(List.of())))
        )
  }

  private void mockEpisContributions() {
    mockServerClient
        .when(request("/contributions"))
        .respond(response()
            .withContentType(APPLICATION_JSON)
            .withBody((mapper.writeValueAsString(List.of())))
        )
  }

  private void mockEpisContactDetails() {
    mockServerClient
        .when(request("/contact-details"))
        .respond(response()
            .withContentType(APPLICATION_JSON)
            .withBody((mapper.writeValueAsString(contactDetailsFixture())))
        )
  }

  private void mockEpisAccountStatement() {
    mockServerClient
        .when(request("/account-statement"))
        .respond(response()
            .withContentType(APPLICATION_JSON)
            .withBody((mapper.writeValueAsString(
                List.of(FundBalanceDto.builder().isin(TULEVA_3RD_PILLAR_FUND_ISIN).build())
            )))
        )
  }
}
