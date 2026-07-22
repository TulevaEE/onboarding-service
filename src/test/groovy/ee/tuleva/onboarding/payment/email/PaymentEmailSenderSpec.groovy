package ee.tuleva.onboarding.payment.email

import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil
import ee.tuleva.onboarding.auth.principal.PrincipalService
import ee.tuleva.onboarding.conversion.UserConversionService
import ee.tuleva.onboarding.epis.contact.ContactDetails
import ee.tuleva.onboarding.epis.contact.ContactDetailsService
import ee.tuleva.onboarding.mandate.email.PillarSuggestion
import ee.tuleva.onboarding.party.PartyId
import ee.tuleva.onboarding.payment.event.PaymentCreatedEvent
import ee.tuleva.onboarding.payment.event.SavingsPaymentCancelledEvent
import ee.tuleva.onboarding.payment.event.SavingsPaymentCreatedEvent
import ee.tuleva.onboarding.payment.event.SavingsPaymentFailedEvent
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON
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
  SavingsFundSuccessEmailResolver savingsFundSuccessEmailResolver = Mock()

  def paymentEmailSender = new PaymentEmailSender(paymentEmailService, conversionService, principalService,
      grantedAuthorityFactory, jwtTokenUtil, contactDetailsService, paymentRateService, savingsFundSuccessEmailResolver)

  def "send emails on payment creation"() {
    given:
    def user = sampleUser().build()
    def payment = aNewSinglePayment()
    def locale = ENGLISH
    def contactDetails = new ContactDetails()
    def conversion = notFullyConverted()
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)

    def paymentCreatedEvent = new PaymentCreatedEvent(this, user, payment, locale)

    1 * contactDetailsService.getContactDetails(user) >> contactDetails
    1 * conversionService.getConversion(user) >> conversion
    1 * paymentRateService.getPaymentRates(user) >> paymentRates

    when:
    paymentEmailSender.onThirdPillarPaymentCreated(paymentCreatedEvent)

    then:
    1 * paymentEmailService.sendThirdPillarPaymentSuccessEmail(user, payment, pillarSuggestion, locale)
  }

  def "does not send emails on payment creation if member fee payment"() {
    given:
    def user = sampleUser().build()
    def payment = aNewSinglePayment()
    payment.paymentType = MEMBER_FEE
    def locale = ENGLISH
    def paymentCreatedEvent = new PaymentCreatedEvent(this, user, payment, locale)

    when:
    paymentEmailSender.onThirdPillarPaymentCreated(paymentCreatedEvent)

    then:
    0 * paymentEmailService.sendThirdPillarPaymentSuccessEmail(_, _, _, _)
  }

  def "send resolved success email on savings payment creation"() {
    given:
    def user = sampleUser().build()
    def locale = ENGLISH
    def contactDetails = new ContactDetails()
    def conversion = notFullyConverted()
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)

    def savingsPaymentCreatedEvent = new SavingsPaymentCreatedEvent(this, user, locale, new PartyId(PERSON, user.personalCode))

    1 * contactDetailsService.getContactDetails(user) >> contactDetails
    1 * conversionService.getConversion(user) >> conversion
    1 * paymentRateService.getPaymentRates(user) >> paymentRates
    1 * savingsFundSuccessEmailResolver.resolve(savingsPaymentCreatedEvent) >> SavingsFundPaymentEmail.personSuccess()

    when:
    paymentEmailSender.onSavingsPaymentCreated(savingsPaymentCreatedEvent)

    then:
    1 * paymentEmailService.sendSavingsFundPaymentEmail(user, SavingsFundPaymentEmail.personSuccess(), pillarSuggestion, locale)
  }

  def "send email on savings payment creation for a child passes the child email with the child name"() {
    given:
    def user = sampleUser().build()
    def locale = ENGLISH
    def contactDetails = new ContactDetails()
    def conversion = notFullyConverted()
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)

    def savingsPaymentCreatedEvent = new SavingsPaymentCreatedEvent(this, user, locale, new PartyId(PERSON, "51107121760"))

    1 * contactDetailsService.getContactDetails(user) >> contactDetails
    1 * conversionService.getConversion(user) >> conversion
    1 * paymentRateService.getPaymentRates(user) >> paymentRates
    1 * savingsFundSuccessEmailResolver.resolve(savingsPaymentCreatedEvent) >> SavingsFundPaymentEmail.childSuccess("Kid Valdma")

    when:
    paymentEmailSender.onSavingsPaymentCreated(savingsPaymentCreatedEvent)

    then:
    1 * paymentEmailService.sendSavingsFundPaymentEmail(user, SavingsFundPaymentEmail.childSuccess("Kid Valdma"), pillarSuggestion, locale)
  }

  def "send email on savings payment cancel"() {
    given:
    def user = sampleUser().build()
    def locale = ENGLISH
    def contactDetails = new ContactDetails()
    def conversion = notFullyConverted()
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)

    def savingsPaymentCancelledEvent = new SavingsPaymentCancelledEvent(this, user, locale)

    1 * contactDetailsService.getContactDetails(user) >> contactDetails
    1 * conversionService.getConversion(user) >> conversion
    1 * paymentRateService.getPaymentRates(user) >> paymentRates

    when:
    paymentEmailSender.onSavingsPaymentCancelled(savingsPaymentCancelledEvent)

    then:
    1 * paymentEmailService.sendSavingsFundPaymentEmail(user, SavingsFundPaymentEmail.cancelled(), pillarSuggestion, locale)
  }

  def "send email on savings payment failure"() {
    given:
    def user = sampleUser().build()
    def locale = ENGLISH
    def contactDetails = new ContactDetails()
    def conversion = notFullyConverted()
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)

    def savingsPaymentFailedEvent = new SavingsPaymentFailedEvent(this, user, locale)

    1 * contactDetailsService.getContactDetails(user) >> contactDetails
    1 * conversionService.getConversion(user) >> conversion
    1 * paymentRateService.getPaymentRates(user) >> paymentRates

    when:
    paymentEmailSender.onSavingsPaymentFailed(savingsPaymentFailedEvent)

    then:
    1 * paymentEmailService.sendSavingsFundPaymentEmail(user, SavingsFundPaymentEmail.failed(), pillarSuggestion, locale)
  }
}
