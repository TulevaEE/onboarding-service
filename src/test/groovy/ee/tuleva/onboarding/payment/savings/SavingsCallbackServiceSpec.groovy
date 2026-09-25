package ee.tuleva.onboarding.payment.savings


import tools.jackson.databind.json.JsonMapper
import com.nimbusds.jose.JWSObject
import ee.tuleva.onboarding.payment.GiftPayments
import ee.tuleva.onboarding.payment.IncomingSavingsPayment
import ee.tuleva.onboarding.payment.SavingsPaymentOutcome
import ee.tuleva.onboarding.payment.SavingsPayments
import ee.tuleva.onboarding.payment.event.SavingsPaymentCreatedEvent
import ee.tuleva.onboarding.payment.provider.montonio.MontonioTokenParser
import ee.tuleva.onboarding.party.PartyId
import ee.tuleva.onboarding.user.UserService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.authentication.BadCredentialsException
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.auth.UserFixture.*
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.*

class SavingsCallbackServiceSpec extends Specification {
  MontonioTokenParser tokenParser = new MontonioTokenParser(JsonMapper.builder().build(), aPaymentProviderConfiguration())
  SavingsCallbackService savingsCallbackService
  SavingsPayments savingsPayments = Mock()
  GiftPayments giftPayments = Mock()
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
        giftPayments,
        eventPublisher,
    )
    giftPayments.findGiftLinkToken(_ as String) >> Optional.empty()
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
    recorded.paid()
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
    recorded.paid()
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
    recorded.paid()
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
    recorded.paid()
  }

  def "a paid token for a payment that is already recorded is accepted without a second receipt"() {
    given:
    def serializedToken = aSerializedSavingsPaymentToken
    1 * savingsPayments.recordIncoming(_) >> false
    when:
    def accepted = savingsCallbackService.processToken(serializedToken)
    then:
    0 * eventPublisher.publishEvent(_)
    accepted.paid()
  }

  def "if token is not paid then no payment is saved"() {
    def serializedToken = aSerializedSavingsPaymentTokenWith(paymentStatus: "PENDING")
    when:
    def recorded = savingsCallbackService.processToken(serializedToken)
    then:
    0 * savingsPayments.recordIncoming(_)
    !recorded.paid()
  }

  def "a paid payment that was not started through a gift link names no gift link"() {
    given:
    def serializedToken = aSerializedSavingsPaymentToken
    savingsPayments.recordIncoming(_) >> true
    userService.findByPersonalCode(_) >> Optional.empty()
    when:
    def recorded = savingsCallbackService.processToken(serializedToken)
    then:
    recorded == new SavingsPaymentOutcome(true, null)
  }

  def "a paid gift names the gift link it was started through"() {
    given:
    def serializedToken = aSerializedSavingsPaymentToken
    savingsPayments.recordIncoming(_) >> true
    userService.findByPersonalCode(_) >> Optional.empty()
    when:
    def recorded = savingsCallbackService.processToken(serializedToken)
    then:
    1 * giftPayments.findGiftLinkToken("description") >> Optional.of("9TY0PX9J")
    recorded == new SavingsPaymentOutcome(true, "9TY0PX9J")
  }

  def "a gift that was not paid names its gift link without being recorded as money in"() {
    given:
    def serializedToken = aSerializedSavingsPaymentTokenWith(paymentStatus: "PENDING")
    when:
    def recorded = savingsCallbackService.processToken(serializedToken)
    then:
    1 * giftPayments.findGiftLinkToken("description") >> Optional.of("9TY0PX9J")
    0 * savingsPayments.recordIncoming(_)
    recorded == new SavingsPaymentOutcome(false, "9TY0PX9J")
  }

  def "if payment type is not SAVINGS then no payment is saved"() {
    def serializedToken = aSerializedSinglePaymentFinishedToken
    when:
    def recorded = savingsCallbackService.processToken(serializedToken)
    then:
    0 * savingsPayments.recordIncoming(_)
    0 * giftPayments.findGiftLinkToken(_)
    recorded == new SavingsPaymentOutcome(false, null)
  }

  @Unroll
  def "paid token without #missing records the payment without those details and sends the receipt"() {
    given:
    def serializedToken = aSerializedSavingsPaymentTokenWithout(fields)
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
    1 * eventPublisher.publishEvent(_ as SavingsPaymentCreatedEvent)
    recorded.paid()

    where:
    missing                | fields
    "sender name and IBAN" | ["senderName", "senderIban"]
    "sender name"          | ["senderName"]
    "sender IBAN"          | ["senderIban"]
  }

  def "rejects a missing or malformed token without recording or publishing anything"() {
    when:
    savingsCallbackService.processToken(malformedToken)
    then:
    thrown(BadCredentialsException)
    0 * savingsPayments.recordIncoming(_)
    0 * giftPayments.findGiftLinkToken(_)
    0 * eventPublisher.publishEvent(_)
    where:
    malformedToken << ["garbage", "", "   ", "a.b.c", null]
  }
}
