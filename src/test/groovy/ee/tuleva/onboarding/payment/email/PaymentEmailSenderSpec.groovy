package ee.tuleva.onboarding.payment.email

import ee.tuleva.onboarding.auth.SecurityContextRunner
import ee.tuleva.onboarding.auth.principal.MinorCannotSelfAuthenticateException
import ee.tuleva.onboarding.conversion.UserConversionService
import ee.tuleva.onboarding.epis.ContactDetails
import ee.tuleva.onboarding.epis.ContactDetailsService
import ee.tuleva.onboarding.mandate.PillarSuggestion
import ee.tuleva.onboarding.party.PartyId
import ee.tuleva.onboarding.payment.event.PaymentCreatedEvent
import ee.tuleva.onboarding.payment.event.SavingsPaymentCancelledEvent
import ee.tuleva.onboarding.payment.event.SavingsPaymentCreatedEvent
import ee.tuleva.onboarding.payment.event.SavingsPaymentFailedEvent
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService
import ee.tuleva.onboarding.analytics.SecondPillarLeavers
import ee.tuleva.onboarding.analytics.RecurringSavers
import ee.tuleva.onboarding.analytics.SaverId
import ee.tuleva.onboarding.contribution.ThirdPillarTaxHeadroom
import ee.tuleva.onboarding.mandate.RecurringPayments
import ee.tuleva.onboarding.mandate.SavingsFundSaverStatus
import spock.lang.Specification

import java.util.Set

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.MEMBER_FEE
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewSinglePayment
import static ee.tuleva.onboarding.paymentrate.PaymentRatesFixture.samplePaymentRates
import static java.util.Locale.ENGLISH

class PaymentEmailSenderSpec extends Specification {

  static final UUID CHILD_LINK_ID = UUID.fromString("33333333-3333-3333-3333-333333333333")
  static final UUID COMPANY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444")

  PaymentEmailService paymentEmailService = Mock()
  UserConversionService conversionService = Mock()
  SecurityContextRunner securityContextRunner = Mock() {
    runAs(_, _) >> { args -> (args[1] as Runnable).run() }
  }
  ContactDetailsService contactDetailsService = Mock()
  SecondPillarPaymentRateService paymentRateService = Mock()
  SavingsFundSuccessEmailResolver savingsFundSuccessEmailResolver = Mock()
  SecondPillarLeavers secondPillarLeavers = Mock() {
    hasLeft(_) >> false
  }
  SavingsFundSaverStatus savingsFundSavers = Mock() {
    isSaver(_) >> false
  }
  ThirdPillarTaxHeadroom thirdPillarTaxHeadroom = Mock(ThirdPillarTaxHeadroom) {
    hasHeadroom(_) >> false
  }
  RecurringSavers recurringSavers = Mock()

  def paymentEmailSender = new PaymentEmailSender(paymentEmailService, conversionService, securityContextRunner, contactDetailsService, paymentRateService, secondPillarLeavers, savingsFundSavers, recurringSavers, thirdPillarTaxHeadroom, savingsFundSuccessEmailResolver)

  def "send emails on payment creation"() {
    given:
    def user = sampleUser().build()
    def payment = aNewSinglePayment()
    def locale = ENGLISH
    def contactDetails = new ContactDetails()
    def conversion = notFullyConverted()
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, false, false, conversion, paymentRates, Set.of(3), false, false)

    def paymentCreatedEvent = new PaymentCreatedEvent(this, user, payment, locale)

    1 * contactDetailsService.getContactDetails(user) >> contactDetails
    1 * conversionService.getConversion(user) >> conversion
    1 * paymentRateService.getPaymentRates(user) >> paymentRates
    1 * recurringSavers.recurringPaymentsOf(user.personalCode, SaverId.person(user.personalCode)) >> new RecurringPayments(true, true)

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

  def "savings payment receipt treats the payer as a saver and scopes the recurring cadence to the paid account"() {
    given:
    def user = sampleUser().build()
    def locale = ENGLISH
    def contactDetails = new ContactDetails()
    def conversion = notFullyConverted()
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, false, false, conversion, paymentRates, [] as Set, false, true, false, new RecurringPayments(true, false), false)

    def savingsPaymentCreatedEvent = new SavingsPaymentCreatedEvent(this, user, locale, new PartyId(PERSON, user.personalCode))

    1 * contactDetailsService.getContactDetails(user) >> contactDetails
    1 * conversionService.getConversion(user) >> conversion
    1 * paymentRateService.getPaymentRates(user) >> paymentRates
    1 * recurringSavers.recurringPaymentsOf(user.personalCode, SaverId.person(user.personalCode)) >> new RecurringPayments(true, false)
    1 * savingsFundSuccessEmailResolver.resolve(savingsPaymentCreatedEvent) >> SavingsFundPaymentEmail.personSuccess()

    when:
    paymentEmailSender.onSavingsPaymentCreated(savingsPaymentCreatedEvent)

    then:
    1 * paymentEmailService.sendSavingsFundPaymentEmail(user, SavingsFundPaymentEmail.personSuccess(), pillarSuggestion, locale)
  }

  def "child payment receipt checks the child's recurring cadence, not the parent's"() {
    given:
    def user = sampleUser().build()
    def locale = ENGLISH
    def contactDetails = new ContactDetails()
    def conversion = notFullyConverted()
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, false, false, conversion, paymentRates, [] as Set, false, true, false, new RecurringPayments(true, false), false)

    def savingsPaymentCreatedEvent = new SavingsPaymentCreatedEvent(this, user, locale, new PartyId(PERSON, "51111111111"))

    1 * contactDetailsService.getContactDetails(user) >> contactDetails
    1 * conversionService.getConversion(user) >> conversion
    1 * paymentRateService.getPaymentRates(user) >> paymentRates
    1 * recurringSavers.recurringPaymentsOf(user.personalCode, SaverId.person("51111111111")) >> new RecurringPayments(true, false)
    1 * savingsFundSuccessEmailResolver.resolve(savingsPaymentCreatedEvent) >> SavingsFundPaymentEmail.childSuccess("Kid Tester", CHILD_LINK_ID)

    when:
    paymentEmailSender.onSavingsPaymentCreated(savingsPaymentCreatedEvent)

    then:
    1 * paymentEmailService.sendSavingsFundPaymentEmail(user, SavingsFundPaymentEmail.childSuccess("Kid Tester", CHILD_LINK_ID), pillarSuggestion, locale)
  }

  def "company payment receipt checks the company's recurring cadence"() {
    given:
    def user = sampleUser().build()
    def locale = ENGLISH
    def contactDetails = new ContactDetails()
    def conversion = notFullyConverted()
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, false, false, conversion, paymentRates, [] as Set, false, true, false, new RecurringPayments(true, true), false)

    def savingsPaymentCreatedEvent = new SavingsPaymentCreatedEvent(this, user, locale, new PartyId(LEGAL_ENTITY, "12345678"))

    1 * contactDetailsService.getContactDetails(user) >> contactDetails
    1 * conversionService.getConversion(user) >> conversion
    1 * paymentRateService.getPaymentRates(user) >> paymentRates
    1 * recurringSavers.recurringPaymentsOf(user.personalCode, new SaverId(SaverId.Type.LEGAL_ENTITY, "12345678")) >> new RecurringPayments(true, true)
    1 * savingsFundSuccessEmailResolver.resolve(savingsPaymentCreatedEvent) >> SavingsFundPaymentEmail.companySuccess("Mesila OÜ", COMPANY_ID)

    when:
    paymentEmailSender.onSavingsPaymentCreated(savingsPaymentCreatedEvent)

    then:
    1 * paymentEmailService.sendSavingsFundPaymentEmail(user, SavingsFundPaymentEmail.companySuccess("Mesila OÜ", COMPANY_ID), pillarSuggestion, locale)
  }

  def "send email on savings payment cancel"() {
    given:
    def user = sampleUser().build()
    def locale = ENGLISH
    def contactDetails = new ContactDetails()
    def conversion = notFullyConverted()
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, false, false, conversion, paymentRates, [] as Set, false, false)

    def savingsPaymentCancelledEvent = new SavingsPaymentCancelledEvent(this, user, locale)

    1 * contactDetailsService.getContactDetails(user) >> contactDetails
    1 * conversionService.getConversion(user) >> conversion
    1 * paymentRateService.getPaymentRates(user) >> paymentRates
    1 * recurringSavers.recurringPaymentsOf(user.personalCode, SaverId.person(user.personalCode)) >> new RecurringPayments(true, true)

    when:
    paymentEmailSender.onSavingsPaymentCancelled(savingsPaymentCancelledEvent)

    then:
    1 * paymentEmailService.sendSavingsFundPaymentEmail(user, SavingsFundPaymentEmail.cancelled(), pillarSuggestion, locale)
  }

  def "send email on savings payment failure without a pillar suggestion"() {
    given:
    def user = sampleUser().build()
    def locale = ENGLISH

    def savingsPaymentFailedEvent = new SavingsPaymentFailedEvent(this, user, locale)

    when:
    paymentEmailSender.onSavingsPaymentFailed(savingsPaymentFailedEvent)

    then:
    1 * paymentEmailService.sendSavingsFundPaymentEmail(user, SavingsFundPaymentEmail.failed(), locale)
    0 * contactDetailsService._
    0 * conversionService._
    0 * paymentRateService._
    0 * securityContextRunner._
  }

  def "send email on savings payment failure for a minor who cannot self authenticate"() {
    given:
    def minor = sampleUser().personalCode("51111111111").build()
    def locale = ENGLISH

    def savingsPaymentFailedEvent = new SavingsPaymentFailedEvent(this, minor, locale)

    securityContextRunner.runAs(_, _) >> { throw new MinorCannotSelfAuthenticateException(minor.personalCode) }

    when:
    paymentEmailSender.onSavingsPaymentFailed(savingsPaymentFailedEvent)

    then:
    noExceptionThrown()
    1 * paymentEmailService.sendSavingsFundPaymentEmail(minor, SavingsFundPaymentEmail.failed(), locale)
  }
}
