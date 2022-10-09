package ee.tuleva.onboarding.payment.email

import ee.tuleva.onboarding.conversion.UserConversionService
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.contact.ContactDetails
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent
import ee.tuleva.onboarding.payment.event.PaymentCreatedEvent
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewPayment
import static java.util.Locale.ENGLISH

class PaymentEmailSenderSpec extends Specification {

  PaymentEmailService paymentEmailService = Mock()
  EpisService episService = Mock()
  UserConversionService conversionService = Mock()

  def paymentEmailSender = new PaymentEmailSender(paymentEmailService, episService, conversionService)

  def "send emails on payment creation"() {
    given:
    def user = sampleUser().build()
    def locale = ENGLISH
    def paymentCreatedEvent = new PaymentCreatedEvent(this, user, aNewPayment(), locale)
    1 * episService.getContactDetails(user) >> new ContactDetails()
    1 * conversionService.getConversion(user) >> notFullyConverted()

    when:
    paymentEmailSender.sendEmails(paymentCreatedEvent)

    then:
    1 * paymentEmailService.sendThirdPillarPaymentSuccessEmail(user, locale)
    1 * paymentEmailService.scheduleThirdPillarSuggestSecondEmail(user, locale)
  }

  def "does not schedule suggestion email when fully converted"() {
    given:
    def user = sampleUser().build()
    def locale = ENGLISH
    def paymentCreatedEvent = new PaymentCreatedEvent(this, user, aNewPayment(), locale)
    1 * episService.getContactDetails(user) >> contactDetailsFixture()
    1 * conversionService.getConversion(user) >> fullyConverted()

    when:
    paymentEmailSender.sendEmails(paymentCreatedEvent)

    then:
    1 * paymentEmailService.sendThirdPillarPaymentSuccessEmail(user, locale)
    0 * paymentEmailService.scheduleThirdPillarSuggestSecondEmail(user, locale)
  }

}
