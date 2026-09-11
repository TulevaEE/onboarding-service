package ee.tuleva.onboarding.mandate.email

import ee.tuleva.onboarding.mandate.PillarSuggestion
import ee.tuleva.onboarding.conversion.ConversionResponse
import ee.tuleva.onboarding.conversion.UserConversionService
import ee.tuleva.onboarding.mandate.MandateContactDetails
import ee.tuleva.onboarding.mandate.MandateContacts
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.MandateFixture
import ee.tuleva.onboarding.mandate.batch.MandateBatch
import ee.tuleva.onboarding.mandate.event.AfterMandateBatchSignedEvent
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent
import ee.tuleva.onboarding.mandate.event.OnMandateBatchFailedEvent
import ee.tuleva.onboarding.paymentrate.PaymentRates
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import java.util.Set

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted
import static ee.tuleva.onboarding.mandate.MandateFixture.aFundPensionOpeningMandateDetails
import static ee.tuleva.onboarding.mandate.MandateFixture.aPartialWithdrawalMandateDetails
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFundPensionOpeningMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandateWithPaymentRate
import static ee.tuleva.onboarding.mandate.MandateFixture.samplePartialWithdrawalMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.thirdPillarMandate
import static ee.tuleva.onboarding.mandate.batch.MandateBatchFixture.aMandateBatch
import ee.tuleva.onboarding.mandate.PillarLeavers
import ee.tuleva.onboarding.mandate.RecurringPayments
import ee.tuleva.onboarding.mandate.RecurringContributions
import ee.tuleva.onboarding.mandate.TaxHeadroom
import ee.tuleva.onboarding.mandate.SavingsFundSaverStatus

import static ee.tuleva.onboarding.mandate.batch.MandateBatchFixture.aSavedMandateBatch
import static ee.tuleva.onboarding.paymentrate.PaymentRatesFixture.samplePaymentRates

class MandateEmailSenderSpec extends Specification {

  MandateContacts mandateContacts = Mock(MandateContacts)
  MandateEmailService mandateEmailService = Mock(MandateEmailService)
  UserConversionService conversionService = Mock(UserConversionService)
  SecondPillarPaymentRateService paymentRateService = Mock(SecondPillarPaymentRateService)
  MandateBatchEmailService mandateBatchEmailService = Mock(MandateBatchEmailService)
  PillarLeavers pillarLeavers = Mock(PillarLeavers) {
    hasLeft(_) >> false
  }
  SavingsFundSaverStatus savingsFundSavers = Mock(SavingsFundSaverStatus) {
    isSaver(_) >> false
  }
  RecurringContributions recurringContributions = Mock(RecurringContributions) {
    recurringPaymentsOf(_) >> new RecurringPayments(true, true)
  }

  TaxHeadroom taxHeadroom = Mock(TaxHeadroom) {
    hasHeadroom(_) >> false
  }
  MandateEmailSender mandateEmailSender = new MandateEmailSender(mandateEmailService, mandateBatchEmailService, mandateContacts, conversionService, paymentRateService, pillarLeavers, savingsFundSavers, recurringContributions, taxHeadroom)

  def "send email when second pillar mandate event was received"() {
    given:
    User user = sampleUser().build()
    Mandate mandate = sampleMandate()
    MandateContactDetails contactDetails = MandateContactDetails.builder().build()
    ConversionResponse conversion = notFullyConverted()
    PaymentRates paymentRates = samplePaymentRates()
    PillarSuggestion pillarSuggestion = new PillarSuggestion(user, false, false, conversion, paymentRates, Set.of(mandate.getPillar()), false, false)

    AfterMandateSignedEvent event = new AfterMandateSignedEvent(this, user, mandate, Locale.ENGLISH)

    1 * mandateContacts.getContactDetails(_) >> contactDetails
    1 * conversionService.getConversion(user) >> conversion
    1 * paymentRateService.getPaymentRates(user) >> paymentRates

    when:
    mandateEmailSender.sendEmail(event)

    then:
    1 * mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)
  }

  def "send email when third pillar mandate event was received"() {
    given:
    User user = sampleUser().build()
    Mandate mandate = thirdPillarMandate()
    MandateContactDetails contactDetails = MandateContactDetails.builder().build()
    ConversionResponse conversion = notFullyConverted()
    PaymentRates paymentRates = samplePaymentRates()
    PillarSuggestion pillarSuggestion = new PillarSuggestion(user, false, false, conversion, paymentRates, Set.of(mandate.getPillar()), false, false)

    AfterMandateSignedEvent event = new AfterMandateSignedEvent(this, user, mandate, Locale.ENGLISH)

    1 * mandateContacts.getContactDetails(_) >> contactDetails
    1 * conversionService.getConversion(event.getUser()) >> conversion
    1 * paymentRateService.getPaymentRates(event.getUser()) >> paymentRates

    when:
    mandateEmailSender.sendEmail(event)

    then:
    1 * mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)
  }


  def "does not suggest a further payment rate increase for the payment rate mandate that concerns it"() {
    given:
    User user = sampleUser().build()
    Mandate mandate = sampleMandateWithPaymentRate()
    MandateContactDetails contactDetails = MandateContactDetails.builder().secondPillarActive(true).build()
    ConversionResponse conversion = notFullyConverted()
    PaymentRates paymentRates = samplePaymentRates()

    AfterMandateSignedEvent event = new AfterMandateSignedEvent(this, user, mandate, Locale.ENGLISH)

    mandateContacts.getContactDetails(_) >> contactDetails
    conversionService.getConversion(user) >> conversion
    paymentRateService.getPaymentRates(user) >> paymentRates

    when:
    mandateEmailSender.sendEmail(event)

    then:
    1 * mandateEmailService.sendMandate(user, mandate, { PillarSuggestion ps -> !ps.isSuggestPaymentRate() }, Locale.ENGLISH)
  }

  def "does not send an individual mandate email when the mandate is part of a batch"() {
    given:
    User user = sampleUser().build()
    Mandate mandate = sampleFundPensionOpeningMandate(aFundPensionOpeningMandateDetails)
    mandate.mandateBatch = aSavedMandateBatch([mandate])
    MandateContactDetails contactDetails = MandateContactDetails.builder().build()
    ConversionResponse conversion = notFullyConverted()
    PaymentRates paymentRates = samplePaymentRates()

    AfterMandateSignedEvent event = new AfterMandateSignedEvent(this, user, mandate, Locale.ENGLISH)

    mandateContacts.getContactDetails(_) >> contactDetails
    conversionService.getConversion(user) >> conversion
    paymentRateService.getPaymentRates(user) >> paymentRates

    when:
    mandateEmailSender.sendEmail(event)

    then:
    0 * mandateEmailService.sendMandate(*_)
  }

  def "sends the batch failed email when a batch failed event was received"() {
    given:
    User user = sampleUser().build()
    Mandate fundPensionMandate = sampleFundPensionOpeningMandate(aFundPensionOpeningMandateDetails)
    MandateBatch mandateBatch = aSavedMandateBatch(List.of(fundPensionMandate))

    OnMandateBatchFailedEvent event = new OnMandateBatchFailedEvent(this, user, mandateBatch, Locale.ENGLISH)

    when:
    mandateEmailSender.sendBatchFailedEmail(event)

    then:
    1 * mandateBatchEmailService.sendMandateBatchFailedEmail(user, mandateBatch, Locale.ENGLISH)
  }

  def "send email when mandate batch event was received"() {
    given:
    User user = sampleUser().build()

    Mandate fundPensionMandate = sampleFundPensionOpeningMandate(aFundPensionOpeningMandateDetails)
    Mandate withdrawalMandate = samplePartialWithdrawalMandate(aPartialWithdrawalMandateDetails)

    MandateBatch mandateBatch = aSavedMandateBatch(List.of(fundPensionMandate,withdrawalMandate))
    MandateContactDetails contactDetails = MandateContactDetails.builder().build()
    ConversionResponse conversion = notFullyConverted()
    PaymentRates paymentRates = samplePaymentRates()
    PillarSuggestion pillarSuggestion = new PillarSuggestion(user, false, false, conversion, paymentRates, [fundPensionMandate.getPillar(), withdrawalMandate.getPillar()] as Set, false, false)

    AfterMandateBatchSignedEvent event = new AfterMandateBatchSignedEvent(this, user, mandateBatch, Locale.ENGLISH)

    1 * mandateContacts.getContactDetails(_) >> contactDetails
    1 * conversionService.getConversion(user) >> conversion
    1 * paymentRateService.getPaymentRates(user) >> paymentRates

    when:
    mandateEmailSender.sendBatchEmail(event)

    then:
    1 * mandateBatchEmailService.sendMandateBatch(user, mandateBatch, pillarSuggestion, Locale.ENGLISH)
  }
}
