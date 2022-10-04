package ee.tuleva.onboarding.payment.provider

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tuleva.onboarding.payment.PaymentRepository
import ee.tuleva.onboarding.payment.event.PaymentCreatedEvent
import ee.tuleva.onboarding.user.UserService
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import static ee.tuleva.onboarding.payment.PaymentFixture.aNewPayment
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.*

class PaymentProviderCallbackServiceSpec extends Specification {
  UserService userService = Mock()
  PaymentProviderCallbackService paymentProviderCallbackService
  PaymentRepository paymentRepository = Mock()
  ApplicationEventPublisher eventPublisher = Mock()

  void setup() {
    paymentProviderCallbackService = new PaymentProviderCallbackService(
        aPaymentProviderConfiguration(),
        userService,
        paymentRepository,
        new ObjectMapper(),
        eventPublisher
    )
  }

  void "if returning payment token is complete and no other payment exists in the database, create one"() {
    given:
    def token = aSerializedCallbackFinalizedToken
    def payment = aNewPayment()
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
    def token = aSerializedCallbackFinalizedToken
    def existingPayment = Optional.of(aNewPayment())
    1 * paymentRepository.findByInternalReference(anInternalReference.getUuid()) >> existingPayment
    when:
    def returnedPayment = paymentProviderCallbackService.processToken(token)
    then:
    returnedPayment == existingPayment
    0 * paymentRepository.save(_)
  }

  def "if payment callback token is pending then do not create a new one"() {
    given:
    def token = aSerializedCallbackPendingToken
    when:
    def returnedPayment = paymentProviderCallbackService.processToken(token)
    then:
    returnedPayment.isEmpty()
    0 * paymentRepository.save(_)
  }

  def "if payment callback token is failed then do not create a new one"() {
    given:
    def token = aSerializedCallbackFailedToken
    when:
    def returnedPayment = paymentProviderCallbackService.processToken(token)
    then:
    returnedPayment.isEmpty()
    0 * paymentRepository.save(_)
  }

  void "publish payment created event"() {
    given:
    def token = aSerializedCallbackFinalizedToken
    def payment = aNewPayment()
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
