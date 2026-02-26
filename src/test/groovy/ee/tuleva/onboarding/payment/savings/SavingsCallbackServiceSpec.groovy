package ee.tuleva.onboarding.payment.savings

import tools.jackson.databind.json.JsonMapper
import com.nimbusds.jose.JWSObject
import ee.tuleva.onboarding.payment.provider.montonio.MontonioTokenParser
import ee.tuleva.onboarding.savings.fund.SavingFundPayment
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserService
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.*

class SavingsCallbackServiceSpec extends Specification {
  MontonioTokenParser tokenParser = new MontonioTokenParser(JsonMapper.builder().build(), aPaymentProviderConfiguration())
  SavingsCallbackService savingsCallbackService
  SavingFundPaymentRepository savingFundPaymentRepository = Mock()
  UserService userService = Mock()
  ApplicationEventPublisher eventPublisher = Mock()

  def savingsChannelConfiguration = new SavingsChannelConfiguration(
      returnUrl: "http://success.url",
      notificationUrl: "http://notification.url",
      accessKey: "test-access-key",
      secretKey: aSecretKey
  )

  void setup() {
    savingsCallbackService = new SavingsCallbackService(
        userService,
        tokenParser,
        savingsChannelConfiguration,
        savingFundPaymentRepository,
        eventPublisher,
    )
  }

  def "if token is paid and no other payment exists in the database, create one"() {
    given:
    def serializedToken = aSerializedSavingsPaymentToken
    1 * savingFundPaymentRepository.findRecentPayments(
        anInternalReference.description
    ) >> []
    1 * userService.findByPersonalCode(anInternalReference.personalCode) >> Optional.empty()
    def token = tokenParser.parse(JWSObject.parse(serializedToken))
    when:
    def returnedPayment = savingsCallbackService.processToken(serializedToken)
    then:
    1 * savingFundPaymentRepository.savePaymentData(_) >> UUID.randomUUID()
    0 * savingFundPaymentRepository.attachUser(_, _) // No user to attach
    def payment = returnedPayment.get()
    payment.amount == token.grandTotal
    payment.currency == token.currency
    payment.description == token.merchantReference.description
    payment.remitterIban == token.senderIban
    payment.remitterName == token.senderName
    payment.beneficiaryIban == null
    payment.beneficiaryName == null
    payment.userId == null // userId not set during creation
  }

  def "if token is paid and user exists, create payment and attach user"() {
    given:
    def serializedToken = aSerializedSavingsPaymentToken
    def mockUser = Mock(User) {
      getId() >> 123L
    }
    def paymentId = UUID.randomUUID()
    1 * savingFundPaymentRepository.findRecentPayments(
        anInternalReference.description
    ) >> []
    1 * userService.findByPersonalCode(anInternalReference.personalCode) >> Optional.of(mockUser)
    def token = tokenParser.parse(JWSObject.parse(serializedToken))
    when:
    def returnedPayment = savingsCallbackService.processToken(serializedToken)
    then:
    1 * savingFundPaymentRepository.savePaymentData(_) >> paymentId
    1 * savingFundPaymentRepository.attachUser(paymentId, 123L) // User attached separately
    1 * eventPublisher.publishEvent(_)
    def payment = returnedPayment.get()
    payment.amount == token.grandTotal
    payment.currency == token.currency
    payment.description == token.merchantReference.description
    payment.remitterIban == token.senderIban
    payment.remitterName == token.senderName
    payment.beneficiaryIban == null
    payment.beneficiaryName == null
    payment.userId == null // userId not set on the returned object, but attached via repository call
  }

  def "if payment already exists then no payment is saved"() {
    given:
    def serializedToken = aSerializedSavingsPaymentToken
    def existingPayment = SavingFundPayment.builder()
        .description(anInternalReference.description)
        .build()
    1 * savingFundPaymentRepository.findRecentPayments(
        anInternalReference.description
    ) >> [existingPayment]
    when:
    def returnedPayment = savingsCallbackService.processToken(serializedToken)
    then:
    0 * savingFundPaymentRepository.savePaymentData(_)
    returnedPayment.isEmpty()
  }

  def "if token is not paid then no payment is saved"() {
    def serializedToken = aSerializedPaymentPendingToken
    when:
    def returnedPayment = savingsCallbackService.processToken(serializedToken)
    then:
    0 * savingFundPaymentRepository.savePaymentData(_)
    returnedPayment.isEmpty()
  }

  def "if payment type is not SAVINGS then no payment is saved"() {
    def serializedToken = aSerializedSinglePaymentFinishedToken
    when:
    def returnedPayment = savingsCallbackService.processToken(serializedToken)
    then:
    0 * savingFundPaymentRepository.savePaymentData(_)
    returnedPayment.isEmpty()
  }


}
