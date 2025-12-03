package ee.tuleva.onboarding.mandate.email

import ee.tuleva.onboarding.conversion.ConversionResponse
import ee.tuleva.onboarding.conversion.UserConversionService
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.contact.ContactDetails
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.MandateFixture
import ee.tuleva.onboarding.mandate.batch.MandateBatch
import ee.tuleva.onboarding.mandate.event.AfterMandateBatchSignedEvent
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent
import ee.tuleva.onboarding.paymentrate.PaymentRates
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted
import static ee.tuleva.onboarding.mandate.MandateFixture.aFundPensionOpeningMandateDetails
import static ee.tuleva.onboarding.mandate.MandateFixture.aPartialWithdrawalMandateDetails
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFundPensionOpeningMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.samplePartialWithdrawalMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.thirdPillarMandate
import static ee.tuleva.onboarding.mandate.batch.MandateBatchFixture.aMandateBatch
import static ee.tuleva.onboarding.mandate.batch.MandateBatchFixture.aSavedMandateBatch
import static ee.tuleva.onboarding.paymentrate.PaymentRatesFixture.samplePaymentRates

class MandateEmailSenderSpec extends Specification {

  EpisService episService = Mock(EpisService)
  MandateEmailService mandateEmailService = Mock(MandateEmailService)
  UserConversionService conversionService = Mock(UserConversionService)
  SecondPillarPaymentRateService paymentRateService = Mock(SecondPillarPaymentRateService)
  MandateBatchEmailService mandateBatchEmailService = Mock(MandateBatchEmailService)

  MandateEmailSender mandateEmailSender = new MandateEmailSender(mandateEmailService, mandateBatchEmailService, episService, conversionService, paymentRateService)

  def "send email when second pillar mandate event was received"() {
    given:
    User user = sampleUser().build()
    Mandate mandate = sampleMandate()
    ContactDetails contactDetails = new ContactDetails()
    ConversionResponse conversion = notFullyConverted()
    PaymentRates paymentRates = samplePaymentRates()
    PillarSuggestion pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)

    AfterMandateSignedEvent event = new AfterMandateSignedEvent(user, mandate, Locale.ENGLISH)

    1 * episService.getContactDetails(_) >> contactDetails
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
    ContactDetails contactDetails = new ContactDetails()
    ConversionResponse conversion = notFullyConverted()
    PaymentRates paymentRates = samplePaymentRates()
    PillarSuggestion pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)

    AfterMandateSignedEvent event = new AfterMandateSignedEvent(user, mandate, Locale.ENGLISH)

    1 * episService.getContactDetails(_) >> contactDetails
    1 * conversionService.getConversion(event.user()) >> conversion
    1 * paymentRateService.getPaymentRates(event.user()) >> paymentRates

    when:
    mandateEmailSender.sendEmail(event)

    then:
    1 * mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)
  }


  def "send email when mandate batch event was received"() {
    given:
    User user = sampleUser().build()

    Mandate fundPensionMandate = sampleFundPensionOpeningMandate(aFundPensionOpeningMandateDetails)
    Mandate withdrawalMandate = samplePartialWithdrawalMandate(aPartialWithdrawalMandateDetails)

    MandateBatch mandateBatch = aSavedMandateBatch(List.of(fundPensionMandate,withdrawalMandate))
    ContactDetails contactDetails = new ContactDetails()
    ConversionResponse conversion = notFullyConverted()
    PaymentRates paymentRates = samplePaymentRates()
    PillarSuggestion pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)

    AfterMandateBatchSignedEvent event = new AfterMandateBatchSignedEvent(user, mandateBatch, Locale.ENGLISH)

    1 * episService.getContactDetails(_) >> contactDetails
    1 * conversionService.getConversion(user) >> conversion
    1 * paymentRateService.getPaymentRates(user) >> paymentRates

    when:
    mandateEmailSender.sendBatchEmail(event)

    then:
    1 * mandateBatchEmailService.sendMandateBatch(user, mandateBatch, pillarSuggestion, Locale.ENGLISH)
  }
}
