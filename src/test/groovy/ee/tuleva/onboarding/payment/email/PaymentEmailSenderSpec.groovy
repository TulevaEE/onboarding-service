package ee.tuleva.onboarding.payment.email

import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil
import ee.tuleva.onboarding.auth.principal.PrincipalService
import ee.tuleva.onboarding.conversion.UserConversionService
import ee.tuleva.onboarding.epis.contact.ContactDetails
import ee.tuleva.onboarding.epis.contact.ContactDetailsService
import ee.tuleva.onboarding.mandate.email.PillarSuggestion
import ee.tuleva.onboarding.mandate.email.persistence.EmailType
import ee.tuleva.onboarding.payment.event.PaymentCreatedEvent
import ee.tuleva.onboarding.payment.event.SavingsPaymentCancelledEvent
import ee.tuleva.onboarding.payment.event.SavingsPaymentCreatedEvent
import ee.tuleva.onboarding.payment.event.SavingsPaymentFailedEvent
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.MEMBER_FEE
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewSinglePayment
import static ee.tuleva.onboarding.paymentrate.PaymentRatesFixture.samplePaymentRates
import static java.util.Locale.ENGLISH

class PaymentEmailSenderSpec extends Specification {

  PaymentEmailService paymentEmailService = Mock()
  UserConversionService conversionService = Mock()
  PrincipalService principalService = Mock()
  GrantedAuthorityFactory grantedAuthorityFactory = Mock()
  JwtTokenUtil jwtTokenUtil = Mock()
  ContactDetailsService contactDetailsService = Mock()
  SecondPillarPaymentRateService paymentRateService = Mock()

  def paymentEmailSender = new PaymentEmailSender(paymentEmailService, conversionService, principalService,
      grantedAuthorityFactory, jwtTokenUtil, contactDetailsService, paymentRateService)

  def "send emails on payment creation"() {
    given:
    def user = sampleUser().build()
    def payment = aNewSinglePayment()
    def locale = ENGLISH
    def contactDetails = new ContactDetails()
    def conversion = notFullyConverted()
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)

    def paymentCreatedEvent = new PaymentCreatedEvent(user, payment, locale)

    1 * contactDetailsService.getContactDetails(user) >> contactDetails
    1 * conversionService.getConversion(user) >> conversion
    1 * paymentRateService.getPaymentRates(user) >> paymentRates

    when:
    paymentEmailSender.sendEmails(paymentCreatedEvent)

    then:
    1 * paymentEmailService.sendThirdPillarPaymentSuccessEmail(user, payment, pillarSuggestion, locale)
  }

  def "does not send emails on payment creation if member fee payment"() {
    given:
    def user = sampleUser().build()
    def payment = aNewSinglePayment()
    payment.paymentType = MEMBER_FEE
    def locale = ENGLISH
    def paymentCreatedEvent = new PaymentCreatedEvent(user, payment, locale)

    when:
    paymentEmailSender.sendEmails(paymentCreatedEvent)

    then:
    0 * paymentEmailService.sendThirdPillarPaymentSuccessEmail(_, _, _)
  }

  def "send email on savings payment creation"() {
    given:
    def user = sampleUser().build()
    def locale = ENGLISH

    def savingsPaymentCreatedEvent = new SavingsPaymentCreatedEvent(user, locale)

    when:
    paymentEmailSender.sendEmails(savingsPaymentCreatedEvent)

    then:
    1 * paymentEmailService.sendSavingsFundPaymentEmail(user, EmailType.SAVINGS_FUND_PAYMENT_SUCCESS, locale)
  }

  def "send email on savings payment cancel"() {
    given:
    def user = sampleUser().build()
    def locale = ENGLISH

    def savingsPaymentCancelledEvent = new SavingsPaymentCancelledEvent(user, locale)

    when:
    paymentEmailSender.sendEmails(savingsPaymentCancelledEvent)

    then:
    1 * paymentEmailService.sendSavingsFundPaymentEmail(user, EmailType.SAVINGS_FUND_PAYMENT_CANCEL, locale)
  }

  def "send email on savings payment failure"() {
    given:
    def user = sampleUser().build()
    def locale = ENGLISH

    def savingsPaymentFailedEvent = new SavingsPaymentFailedEvent(user, locale)

    when:
    paymentEmailSender.sendEmails(savingsPaymentFailedEvent)

    then:
    1 * paymentEmailService.sendSavingsFundPaymentEmail(user, EmailType.SAVINGS_FUND_PAYMENT_FAIL, locale)
  }
}
