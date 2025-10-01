package ee.tuleva.onboarding.payment.savings

import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jose.JWSObject
import ee.tuleva.onboarding.payment.provider.montonio.MontonioTokenParser
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository
import spock.lang.Specification

import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aPaymentProviderConfiguration
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aSecretKey
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aSerializedPaymentPendingToken
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aSerializedSavingsPaymentToken
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aSerializedSinglePaymentFinishedToken
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.getAnInternalReference

class SavingsCallbackServiceSpec extends Specification {
  MontonioTokenParser tokenParser = new MontonioTokenParser(new ObjectMapper(), aPaymentProviderConfiguration())
  SavingsCallbackService savingsCallbackService
  SavingFundPaymentRepository savingFundPaymentRepository = Mock()

  def savingsChannelConfiguration = new SavingsChannelConfiguration(
      returnUrl: "http://success.url",
      notificationUrl: "http://notification.url",
      accessKey: "test-access-key",
      secretKey: aSecretKey
  )

  void setup() {
    savingsCallbackService = new SavingsCallbackService(
        tokenParser,
        savingsChannelConfiguration,
        savingFundPaymentRepository
    )
  }

  def "if token is paid and no other payment exists in the database, create one"() {
    given:
    def serializedToken = aSerializedSavingsPaymentToken
    1 * savingFundPaymentRepository.existsByRemitterIdCodeAndDescription(
        anInternalReference.recipientPersonalCode, anInternalReference.description
    ) >> false
    def token = tokenParser.parse(JWSObject.parse(serializedToken))
    when:
    def returnedPayment = savingsCallbackService.processToken(serializedToken)
    then:
    1 * savingFundPaymentRepository.save(_)
    def payment = returnedPayment.get()
    payment.amount == token.grandTotal
    payment.currency == token.currency
    payment.description == token.merchantReference.description
    payment.remitterIban == token.senderIban
    payment.remitterName == token.senderName
    payment.beneficiaryIban == null
    payment.beneficiaryName == null
  }

  def "if payment already exists then no payment is saved"() {
    given:
    def serializedToken = aSerializedSavingsPaymentToken
    1 * savingFundPaymentRepository.existsByRemitterIdCodeAndDescription(
        anInternalReference.recipientPersonalCode, anInternalReference.description
    ) >> true
    when:
    def returnedPayment = savingsCallbackService.processToken(serializedToken)
    then:
    0 * savingFundPaymentRepository.save(_)
    returnedPayment.isEmpty()
  }

  def "if token is not paid then no payment is saved"() {
    def serializedToken = aSerializedPaymentPendingToken
    when:
    def returnedPayment = savingsCallbackService.processToken(serializedToken)
    then:
    0 * savingFundPaymentRepository.save(_)
    returnedPayment.isEmpty()
  }

  def "if payment type is not SAVINGS then no payment is saved"() {
    def serializedToken = aSerializedSinglePaymentFinishedToken
    when:
    def returnedPayment = savingsCallbackService.processToken(serializedToken)
    then:
    0 * savingFundPaymentRepository.save(_)
    returnedPayment.isEmpty()
  }


}
