package ee.tuleva.onboarding.payment.provider.montonio

import tools.jackson.databind.json.JsonMapper
import ee.tuleva.onboarding.payment.Payment
import ee.tuleva.onboarding.payment.PaymentRepository
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewSinglePayment
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.*

class MontonioCallbackServiceSpec extends Specification {

  MontonioPaymentChannelConfiguration montonioPaymentChannelConfiguration = aPaymentProviderConfiguration()
  UserService userService = Mock(UserService)
  PaymentRepository paymentRepository = Mock(PaymentRepository)
  JsonMapper objectMapper = JsonMapper.builder().build()
  ApplicationEventPublisher eventPublisher = Mock(ApplicationEventPublisher)

  MontonioCallbackService service = new MontonioCallbackService(
      montonioPaymentChannelConfiguration,
      userService,
      paymentRepository,
      objectMapper,
      eventPublisher
  )

  def "processToken: creates a new payment when it doesn't exist"() {
    given:
    UUID internalReferenceUuid = anInternalReference.getUuid()
    User user = sampleUser().build()
    Payment payment = aNewSinglePayment()

    when:
    def result = service.processToken(aSerializedMemberPaymentFinishedToken)

    then:
    1 * paymentRepository.findByInternalReference(internalReferenceUuid) >> Optional.empty()
    1 * userService.findByPersonalCode(anInternalReference.getPersonalCode()) >> Optional.of(user)
    1 * paymentRepository.save(_ as Payment) >> payment
    1 * eventPublisher.publishEvent(_)

    result.isPresent()
    result.get() == payment
  }

  def "processToken: returns existing payment when it already exists"() {
    given:
    UUID internalReferenceUuid = anInternalReference.getUuid()
    Payment existingPayment = aNewSinglePayment()

    when:
    def result = service.processToken(aSerializedMemberPaymentFinishedToken)

    then:
    1 * paymentRepository.findByInternalReference(internalReferenceUuid) >> Optional.of(existingPayment)
    0 * paymentRepository.save(_)
    0 * eventPublisher.publishEvent(_)

    result.isPresent()
    result.get() == existingPayment
  }

  def "processToken: handles race condition by catching duplicate key exception and retrying"() {
    given:
    UUID internalReferenceUuid = anInternalReference.getUuid()
    User user = sampleUser().build()
    Payment existingPayment = aNewSinglePayment()

    when:
    def result = service.processToken(aSerializedMemberPaymentFinishedToken)

    then: "first check finds no existing payment"
    1 * paymentRepository.findByInternalReference(internalReferenceUuid) >> Optional.empty()

    and: "user lookup succeeds"
    1 * userService.findByPersonalCode(anInternalReference.getPersonalCode()) >> Optional.of(user)

    and: "save throws duplicate key exception (race condition)"
    1 * paymentRepository.save(_ as Payment) >> {
      throw new DataIntegrityViolationException("duplicate key value violates unique constraint \"payment_internal_reference_key\"")
    }

    and: "retry finds the existing payment created by the other thread"
    1 * paymentRepository.findByInternalReference(internalReferenceUuid) >> Optional.of(existingPayment)

    and: "no event is published since we didn't create the payment"
    0 * eventPublisher.publishEvent(_)

    and: "returns the existing payment"
    result.isPresent()
    result.get() == existingPayment
  }

  def "processToken: rethrows exception if payment not found after duplicate key error"() {
    given:
    UUID internalReferenceUuid = anInternalReference.getUuid()
    User user = sampleUser().build()
    DataIntegrityViolationException duplicateException = new DataIntegrityViolationException("duplicate key")

    when:
    service.processToken(aSerializedMemberPaymentFinishedToken)

    then: "first check finds no existing payment"
    1 * paymentRepository.findByInternalReference(internalReferenceUuid) >> Optional.empty()

    and: "user lookup succeeds"
    1 * userService.findByPersonalCode(anInternalReference.getPersonalCode()) >> Optional.of(user)

    and: "save throws duplicate key exception"
    1 * paymentRepository.save(_ as Payment) >> { throw duplicateException }

    and: "retry finds no payment (should not happen in practice)"
    1 * paymentRepository.findByInternalReference(internalReferenceUuid) >> Optional.empty()

    and: "rethrows the original exception"
    def exception = thrown(DataIntegrityViolationException)
    exception == duplicateException
  }

  def "processToken: returns empty when payment is not finalized"() {
    when:
    def result = service.processToken(aSerializedPaymentPendingToken)

    then:
    0 * paymentRepository.findByInternalReference(_)
    0 * paymentRepository.save(_)

    result.isEmpty()
  }
}
