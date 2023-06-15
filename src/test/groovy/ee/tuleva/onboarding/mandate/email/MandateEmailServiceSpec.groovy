package ee.tuleva.onboarding.mandate.email

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import ee.tuleva.onboarding.deadline.MandateDeadlinesService
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailService
import ee.tuleva.onboarding.mandate.email.scheduledEmail.ScheduledEmailType
import ee.tuleva.onboarding.notification.email.EmailService
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted
import static ee.tuleva.onboarding.deadline.MandateDeadlinesFixture.sampleDeadlines
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.thirdPillarMandate
import static java.time.ZoneOffset.UTC
import static java.time.temporal.ChronoUnit.DAYS
import static java.time.temporal.ChronoUnit.HOURS

class MandateEmailServiceSpec extends Specification {

  EmailService emailService = Mock()
  ScheduledEmailService scheduledEmailService = Mock()
  FundRepository fundRepository = Mock()
  MandateDeadlinesService mandateDeadlinesService = Mock()
  def now = Instant.parse("2021-09-01T10:06:01Z")

  MandateEmailService mandateEmailService = new MandateEmailService(emailService,
      scheduledEmailService,
      Clock.fixed(now, UTC),
      fundRepository,
      mandateDeadlinesService)

  def "Send second pillar mandate email"() {
    given:
    def user = sampleUser().build()
    def conversion = notFullyConverted()
    def contactDetails = contactDetailsFixture()
    def mandate = sampleMandate()
    def pillarSuggestion = new PillarSuggestion(2, user, contactDetails, conversion)
    def message = new MandrillMessage()
    def mergeVars = [
        fname             : user.firstName,
        lname             : user.lastName,
        suggestMembership : false,
        transferDate      : "03.05.2021",
        suggestThirdPillar: true
    ]
    def tags = ["mandate", "pillar_2", "suggest_3"]

    mandateDeadlinesService.getDeadlines(mandate.createdDate) >> sampleDeadlines()

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)

    then:
    1 * emailService.newMandrillMessage(user.email, "second_pillar_mandate_en", mergeVars, tags, !null) >> message
    1 * emailService.send(user, message, "second_pillar_mandate_en")
  }

  def "mandate tagging for 2nd pillar mandates"() {
    given:
    def pillarSuggestion = Mock(PillarSuggestion)
    pillarSuggestion.isSuggestPillar() >> suggestPillar
    pillarSuggestion.isSuggestMembership() >> suggestMembership

    when:
    def tags = mandateEmailService.getSecondPillarMandateTags(pillarSuggestion)

    then:
    tags == expectedTags

    where:
    suggestPillar | suggestMembership || expectedTags
    false         | false             || ["mandate", "pillar_2"]
    false         | true              || ["mandate", "pillar_2", "suggest_member"]
    true          | false             || ["mandate", "pillar_2", "suggest_3"]
    true          | true              || ["mandate", "pillar_2", "suggest_member", "suggest_3"]
  }

  def "schedule third pillar payment reminder email"() {
    given:
    def user = sampleUser().build()
    def conversion = fullyConverted()
    def contactDetails = contactDetailsFixture()
    def mandate = thirdPillarMandate()
    def pillarSuggestion = new PillarSuggestion(3, user, contactDetails, conversion)
    def message = new MandrillMessage()
    def mergeVars = [fname: user.firstName, lname: user.lastName]
    def tags = ["pillar_3.1", "reminder"]
    def locale = Locale.ENGLISH
    def sendAt = now.plus(1, HOURS)

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, locale)

    then:
    1 * emailService.newMandrillMessage(user.email, "third_pillar_payment_reminder_mandate_en", mergeVars, tags, !null) >> message
    1 * emailService.send(user, message, "third_pillar_payment_reminder_mandate_en", sendAt) >> Optional.of("123")
    1 * scheduledEmailService.create(user, "123", ScheduledEmailType.REMIND_THIRD_PILLAR_PAYMENT, mandate)
  }

  def "schedule third pillar suggest second pillar email"() {
    given:
    def user = sampleUser().build()
    def message = new MandrillMessage()
    def mergeVars = [fname: user.firstName, lname: user.lastName]
    def tags = ["pillar_3.1", "suggest_2"]
    def locale = Locale.ENGLISH
    def sendAt = now.plus(3, DAYS)

    when:
    mandateEmailService.scheduleThirdPillarSuggestSecondEmail(user, locale)

    then:
    1 * emailService.newMandrillMessage(user.email, "third_pillar_suggest_second_en", mergeVars, tags, null) >> message
    1 * emailService.send(user, message, "third_pillar_suggest_second_en", sendAt) >> Optional.of("123")
    1 * scheduledEmailService.create(user, "123", ScheduledEmailType.SUGGEST_SECOND_PILLAR)
  }

  def "Send third pillar suggest second pillar email"() {
    given:
    def user = sampleUser().build()

    PillarSuggestion pillarSuggestion = Mock()
    pillarSuggestion.isSuggestPillar() >> suggestPillar

    def mandate = thirdPillarMandate()
    def paymentReminder = new MandrillMessage()
    def suggestSecond = new MandrillMessage()
    def locale = Locale.ENGLISH
    def mergeVars = [fname: user.firstName, lname: user.lastName]
    1 * emailService.newMandrillMessage(user.email, "third_pillar_payment_reminder_mandate_en", mergeVars, ["pillar_3.1", "reminder"], !null) >> paymentReminder
    1 * emailService.send(user, paymentReminder, "third_pillar_payment_reminder_mandate_en", now.plus(1, HOURS)) >> Optional.of("123")
    1 * scheduledEmailService.create(user, "123", ScheduledEmailType.REMIND_THIRD_PILLAR_PAYMENT, mandate)

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, locale)

    then:
    callCount * emailService.newMandrillMessage(user.email, "third_pillar_suggest_second_en", mergeVars, ["pillar_3.1", "suggest_2"], null) >> suggestSecond
    callCount * emailService.send(user, suggestSecond, "third_pillar_suggest_second_en", now.plus(3, DAYS)) >> Optional.of("234")
    callCount * scheduledEmailService.create(user, "234", ScheduledEmailType.SUGGEST_SECOND_PILLAR)

    where:
    suggestPillar | callCount
    true          | 1
    false         | 0
  }

  def "Sends two third pillar emails"() {
    given:
    def user = sampleUser().build()
    def conversion = notFullyConverted()
    def contactDetails = contactDetailsFixture()
    def pillarSuggestion = new PillarSuggestion(2, user, contactDetails, conversion)
    emailService.newMandrillMessage(*_) >> new MandrillMessage()

    when:
    mandateEmailService.sendMandate(user, thirdPillarMandate(), pillarSuggestion, Locale.ENGLISH)

    then:
    2 * emailService.send(*_) >> Optional.of("123")
  }

}
