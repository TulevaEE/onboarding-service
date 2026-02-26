package ee.tuleva.onboarding.payment.provider

import tools.jackson.databind.json.JsonMapper
import ee.tuleva.onboarding.payment.PaymentRepository
import ee.tuleva.onboarding.payment.event.PaymentCreatedEvent
import ee.tuleva.onboarding.payment.provider.montonio.MontonioCallbackService
import ee.tuleva.onboarding.user.UserService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.authentication.BadCredentialsException
import spock.lang.Specification

import static ee.tuleva.onboarding.payment.PaymentFixture.aNewMemberPayment
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.*

class MontonioNotificationBodyServiceSpec extends Specification {
  UserService userService = Mock()
  MontonioCallbackService paymentProviderCallbackService
  PaymentRepository paymentRepository = Mock()
  ApplicationEventPublisher eventPublisher = Mock()

  void setup() {
    paymentProviderCallbackService = new MontonioCallbackService(
        aPaymentProviderConfiguration(),
        userService,
        paymentRepository,
        JsonMapper.builder().build(),
        eventPublisher
    )
  }

  def "it rejects payment token with invalid signature"() {
    given:
    def token = anInvalidSinglePaymentFinishedToken
    when:
    paymentProviderCallbackService.processToken(token)
    then:
    thrown(BadCredentialsException)
  }

  def "if returning payment token is complete and no other payment exists in the database, create one"() {
    given:
    def token = aSerializedMemberPaymentFinishedToken
    def payment = aNewMemberPayment()
    1 * userService.findByPersonalCode(anInternalReference.getPersonalCode()) >>
        Optional.of(payment.user)
    1 * paymentRepository.findByInternalReference(anInternalReference.getUuid()) >> Optional.empty()
    1 * paymentRepository.save(payment) >> payment
    when:
    def returnedPayment = paymentProviderCallbackService.processToken(token)
    then:
    returnedPayment.get() == payment

  }

  def "if payment with a given internal reference exists, then do not create a new one"() {
    given:
    def token = aSerializedMemberPaymentFinishedToken
    def existingPayment = Optional.of(aNewMemberPayment())
    1 * paymentRepository.findByInternalReference(anInternalReference.getUuid()) >> existingPayment
    when:
    def returnedPayment = paymentProviderCallbackService.processToken(token)
    then:
    returnedPayment == existingPayment
    0 * paymentRepository.save(_)
  }

  def "if payment callback token is pending then do not create a new one"() {
    given:
    def token = aSerializedPaymentPendingToken
    when:
    def returnedPayment = paymentProviderCallbackService.processToken(token)
    then:
    returnedPayment.isEmpty()
    0 * paymentRepository.save(_)
  }

  def "if payment callback token is failed then do not create a new one"() {
    given:
    def token = aSerializedPaymentFailedToken
    when:
    def returnedPayment = paymentProviderCallbackService.processToken(token)
    then:
    returnedPayment.isEmpty()
    0 * paymentRepository.save(_)
  }

  def "publish payment created event"() {
    given:
    def token = aSerializedMemberPaymentFinishedToken
    def payment = aNewMemberPayment()
    def user = payment.user
    userService.findByPersonalCode(anInternalReference.getPersonalCode()) >> Optional.of(user)
    paymentRepository.findByInternalReference(anInternalReference.getUuid()) >> Optional.empty()
    paymentRepository.save(payment) >> payment
    when:
    paymentProviderCallbackService.processToken(token)
    then:
    1 * eventPublisher.publishEvent({ it instanceof PaymentCreatedEvent && it.locale == Locale.ENGLISH })
  }

}
