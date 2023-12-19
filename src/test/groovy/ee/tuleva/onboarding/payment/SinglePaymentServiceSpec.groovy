package ee.tuleva.onboarding.payment

import ee.tuleva.onboarding.payment.provider.PaymentProviderService
import ee.tuleva.onboarding.payment.recurring.RecurringPaymentService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.PARTNER
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.*
import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentData

class SinglePaymentServiceSpec extends Specification {

  RecurringPaymentService recurringPaymentService = Mock()
  PaymentProviderService paymentProviderService = Mock()
  SinglePaymentService singlePaymentService = new SinglePaymentService(recurringPaymentService, paymentProviderService)


  def "can get a partner payment link"() {
    given:
    def person = samplePerson
    def paymentData = aPaymentData().tap { paymentChannel = PARTNER }
    def json = new PaymentLink("""{"json":true}""")
    recurringPaymentService.getPaymentLink(paymentData, person) >> json

    when:
    def returnedLink = singlePaymentService.getPaymentLink(paymentData, person)

    then:
    returnedLink == json
  }

  def "can get a single payment link"() {
    given:
    def person = samplePerson
    def paymentData = aPaymentData().tap { type = SINGLE }
    def link = new PaymentLink("https://single.payment.url")
    paymentProviderService.getPaymentLink(paymentData, person) >> link

    when:
    def returnedLink = singlePaymentService.getPaymentLink(paymentData, person)

    then:
    returnedLink == link
  }
}
