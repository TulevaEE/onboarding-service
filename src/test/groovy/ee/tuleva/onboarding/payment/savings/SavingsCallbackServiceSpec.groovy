package ee.tuleva.onboarding.payment.savings


import tools.jackson.databind.json.JsonMapper
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.MACSigner
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import ee.tuleva.onboarding.payment.IncomingSavingsPayment
import ee.tuleva.onboarding.payment.SavingsPayments
import ee.tuleva.onboarding.payment.provider.montonio.MontonioTokenParser
import ee.tuleva.onboarding.party.PartyId
import ee.tuleva.onboarding.user.UserService
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.*
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.*

class SavingsCallbackServiceSpec extends Specification {
  MontonioTokenParser tokenParser = new MontonioTokenParser(JsonMapper.builder().build(), aPaymentProviderConfiguration())
  SavingsCallbackService savingsCallbackService
  SavingsPayments savingsPayments = Mock()
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
        savingsPayments,
        eventPublisher,
    )
  }

  def "if token is paid and no other payment exists in the database, create one and attach recipient party"() {
    given:
    def serializedToken = aSerializedSavingsPaymentToken
    def token = tokenParser.parse(JWSObject.parse(serializedToken))
    def expectedPayment = new IncomingSavingsPayment(
        token.senderName,
        token.senderIban,
        token.merchantReference.description,
        token.grandTotal,
        token.currency,
        new PartyId(PartyId.Type.PERSON, anInternalReference.recipientPersonalCode))
    1 * userService.findByPersonalCode(anInternalReference.personalCode) >> Optional.empty()
    when:
    def recorded = savingsCallbackService.processToken(serializedToken)
    then:
    1 * savingsPayments.recordIncoming(expectedPayment) >> true
    0 * eventPublisher.publishEvent(_)
    recorded
  }

  def "if token is paid and user exists, create payment, attach recipient party, and send email"() {
    given:
    def serializedToken = aSerializedSavingsPaymentToken
    def mockUser = sampleUser().personalCode("38812121215").build()
    def token = tokenParser.parse(JWSObject.parse(serializedToken))
    def expectedPayment = new IncomingSavingsPayment(
        token.senderName,
        token.senderIban,
        token.merchantReference.description,
        token.grandTotal,
        token.currency,
        new PartyId(PartyId.Type.PERSON, anInternalReference.recipientPersonalCode))
    1 * userService.findByPersonalCode(anInternalReference.personalCode) >> Optional.of(mockUser)
    when:
    def recorded = savingsCallbackService.processToken(serializedToken)
    then:
    1 * savingsPayments.recordIncoming(expectedPayment) >> true
    1 * eventPublisher.publishEvent(_)
    recorded
  }

  def "company payment attaches LEGAL_ENTITY party and sends email to payer"() {
    given:
    def serializedToken = aSerializedCompanySavingsPaymentToken
    def mockUser = sampleUser().personalCode("38812121215").build()
    def token = tokenParser.parse(JWSObject.parse(serializedToken))
    def expectedPayment = new IncomingSavingsPayment(
        token.senderName,
        token.senderIban,
        token.merchantReference.description,
        token.grandTotal,
        token.currency,
        new PartyId(PartyId.Type.LEGAL_ENTITY, "12345678"))
    1 * userService.findByPersonalCode(aCompanySavingsInternalReference.personalCode) >> Optional.of(mockUser)
    when:
    def recorded = savingsCallbackService.processToken(serializedToken)
    then:
    1 * savingsPayments.recordIncoming(expectedPayment) >> true
    1 * eventPublisher.publishEvent(_)
    recorded
  }

  def "company payment with a legacy reference missing recipientPartyType infers LEGAL_ENTITY from the registry code"() {
    given:
    def serializedToken = aSerializedLegacyCompanySavingsPaymentToken
    def mockUser = sampleUser().personalCode("38812121215").build()
    def token = tokenParser.parse(JWSObject.parse(serializedToken))
    def expectedPayment = new IncomingSavingsPayment(
        token.senderName,
        token.senderIban,
        token.merchantReference.description,
        token.grandTotal,
        token.currency,
        new PartyId(PartyId.Type.LEGAL_ENTITY, "12345678"))
    1 * userService.findByPersonalCode(aCompanySavingsInternalReference.personalCode) >> Optional.of(mockUser)
    when:
    def recorded = savingsCallbackService.processToken(serializedToken)
    then:
    1 * savingsPayments.recordIncoming(expectedPayment) >> true
    1 * eventPublisher.publishEvent({ it.recipient == new PartyId(PartyId.Type.LEGAL_ENTITY, "12345678") })
    recorded
  }

  def "if payment already exists then no payment is saved"() {
    given:
    def serializedToken = aSerializedSavingsPaymentToken
    1 * savingsPayments.recordIncoming(_) >> false
    when:
    def recorded = savingsCallbackService.processToken(serializedToken)
    then:
    0 * eventPublisher.publishEvent(_)
    !recorded
  }

  def "if token is not paid then no payment is saved"() {
    def serializedToken = aSerializedPaymentPendingToken
    when:
    def recorded = savingsCallbackService.processToken(serializedToken)
    then:
    0 * savingsPayments.recordIncoming(_)
    !recorded
  }

  def "if payment type is not SAVINGS then no payment is saved"() {
    def serializedToken = aSerializedSinglePaymentFinishedToken
    when:
    def recorded = savingsCallbackService.processToken(serializedToken)
    then:
    0 * savingsPayments.recordIncoming(_)
    !recorded
  }

  def "paid token without sender details is accepted and recording defers to statement processing"() {
    given:
    def serializedToken = withoutSenderDetails(aSerializedSavingsPaymentToken)

    when:
    def accepted = savingsCallbackService.processToken(serializedToken)

    then:
    0 * savingsPayments.recordIncoming(_)
    0 * eventPublisher.publishEvent(_)
    accepted
  }

  private String withoutSenderDetails(String serializedToken) {
    def original = JWSObject.parse(serializedToken)
    def payload = new JsonSlurper().parseText(original.payload.toString()) as Map
    payload.remove("senderName")
    payload.remove("senderIban")
    def jws = new JWSObject(original.header, new Payload(JsonOutput.toJson(payload)))
    jws.sign(new MACSigner(aSecretKey.getBytes()))
    return jws.serialize()
  }
}
