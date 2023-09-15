package ee.tuleva.onboarding.payment

import ee.tuleva.onboarding.payment.provider.PaymentProviderCallbackService
import ee.tuleva.onboarding.payment.provider.PaymentProviderService
import ee.tuleva.onboarding.payment.recurring.RecurringPaymentService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.MEMBER_FEE
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.RECURRING
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SINGLE
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewSinglePayment
import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentData
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aSerializedPaymentProviderToken

class PaymentServiceSpec extends Specification {

  PaymentRepository paymentRepository = Mock()
  PaymentProviderService paymentProviderService = Mock()
  RecurringPaymentService recurringPaymentService = Mock()
  PaymentProviderCallbackService paymentProviderCallbackService = Mock()

  PaymentService paymentService = new PaymentService(
      paymentRepository, paymentProviderService, recurringPaymentService, paymentProviderCallbackService)

  def "can get payments"() {
    given:
    def person = samplePerson()
    def payment = aNewSinglePayment()
    paymentRepository.findAllByRecipientPersonalCode(person.personalCode) >> [payment]
    when:
    def payments = paymentService.getPayments(person)
    then:
    payments == [payment]
  }

  def "can get a single payment link"() {
    given:
    def person = samplePerson
    def paymentData = aPaymentData.tap { type = SINGLE }
    def link = new PaymentLink("https://single.payment.url")
    paymentProviderService.getPaymentLink(paymentData, person) >> link

    when:
    def returnedLink = paymentService.getLink(paymentData, person)

    then:
    returnedLink == link
  }

  def "can get a member payment link"() {
    given:
    def person = samplePerson
    def paymentData = aPaymentData.tap { type = MEMBER_FEE }
    def link = new PaymentLink("https://member.payment.url")
    paymentProviderService.getPaymentLink(paymentData, person) >> link

    when:
    def returnedLink = paymentService.getLink(paymentData, person)

    then:
    returnedLink == link
  }

  def "can get a recurring payment link"() {
    given:
    def person = samplePerson
    def paymentData = aPaymentData.tap { type = RECURRING }
    def link = new PaymentLink("https://recurring.payment.url")
    recurringPaymentService.getPaymentLink(paymentData, person) >> link

    when:
    def returnedLink = paymentService.getLink(paymentData, person)

    then:
    returnedLink == link
  }

  def "can process a payment provider token"() {
    given:
    def token = aSerializedPaymentProviderToken
    def payment = Optional.of(aNewSinglePayment())
    paymentProviderCallbackService.processToken(token) >> payment

    when:
    def returnedPayment = paymentService.processToken(token)

    then:
    returnedPayment == payment
  }
}
