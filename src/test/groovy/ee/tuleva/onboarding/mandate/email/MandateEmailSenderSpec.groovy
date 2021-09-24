package ee.tuleva.onboarding.mandate.email

import ee.tuleva.onboarding.conversion.UserConversionService
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.contact.UserPreferences
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate

class MandateEmailSenderSpec extends Specification {
  EpisService episService = Mock(EpisService)
  MandateEmailService mandateEmailService = Mock(MandateEmailService)
  UserConversionService conversionService = Mock(UserConversionService)

  MandateEmailSender mandateEmailSender = new MandateEmailSender(mandateEmailService, episService, conversionService)

  def "send email when second pillar mandate event was received"() {
    given:
    User user = sampleUser().build()
    Mandate mandate = sampleMandate()

    UserPreferences contactDetails = new UserPreferences()

    AfterMandateSignedEvent event = new AfterMandateSignedEvent(this, user, mandate, Locale.ENGLISH)
    1 * episService.getContactDetails(_) >> contactDetails

    def conversion = notFullyConverted()
    1 * conversionService.getConversion(event.getUser()) >> conversion

    def pillarSuggestion = new PillarSuggestion(3, user, contactDetails, conversion)

    when:
    mandateEmailSender.sendEmail(event)

    then:
    1 * mandateEmailService.sendMandate(user, mandate, pillarSuggestion, contactDetails, Locale.ENGLISH)
  }

  def "send email when third pillar mandate event was received"() {
    given:
    User user = sampleUser().build()
    Mandate mandate = sampleMandate()

    UserPreferences contactDetails = new UserPreferences()

    AfterMandateSignedEvent event = new AfterMandateSignedEvent(this, user, mandate, Locale.ENGLISH)
    1 * episService.getContactDetails(_) >> contactDetails

    def conversion = notFullyConverted()
    1 * conversionService.getConversion(event.getUser()) >> conversion

    PillarSuggestion pillarSuggestion = new PillarSuggestion(2, user, contactDetails, conversion)

    when:
    mandateEmailSender.sendEmail(event)

    then:
    1 * mandateEmailService.sendMandate(user, mandate, pillarSuggestion, contactDetails, Locale.ENGLISH)
  }
}
