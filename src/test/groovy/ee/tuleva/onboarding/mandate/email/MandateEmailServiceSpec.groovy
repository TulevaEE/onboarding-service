package ee.tuleva.onboarding.mandate.email

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.auth.principal.AuthenticationHolder
import ee.tuleva.onboarding.deadline.MandateDeadlinesService
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService
import ee.tuleva.onboarding.mandate.email.persistence.EmailType
import ee.tuleva.onboarding.notification.email.EmailService
import ee.tuleva.onboarding.paymentrate.PaymentRates
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notConverted
import static ee.tuleva.onboarding.deadline.MandateDeadlinesFixture.sampleDeadlines
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.MandateFixture.*
import static java.time.ZoneOffset.UTC
import static java.time.temporal.ChronoUnit.DAYS
import static java.time.temporal.ChronoUnit.HOURS

class MandateEmailServiceSpec extends Specification {

  EmailService emailService = Mock()
  EmailPersistenceService emailPersistenceService = Mock()
  FundRepository fundRepository = Mock()
  MandateDeadlinesService mandateDeadlinesService = Mock()
  SecondPillarPaymentRateService secondPillarPaymentRateService = Mock()
  AuthenticationHolder authenticationHolder = Mock()
  def now = Instant.parse("2021-09-01T10:06:01Z")

  MandateEmailService mandateEmailService = new MandateEmailService(emailService,
      emailPersistenceService,
      Clock.fixed(now, UTC),
      fundRepository,
      mandateDeadlinesService,
      secondPillarPaymentRateService,
      authenticationHolder)

  def "Send second pillar mandate email"() {
    given:
    def user = sampleUser().build()
    def conversion = notConverted()
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
    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    mandateDeadlinesService.getDeadlines(mandate.createdDate) >> sampleDeadlines()

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)

    then:
    1 * emailService.newMandrillMessage(user.email, "second_pillar_mandate_en", mergeVars, tags, !null) >> message
    1 * emailService.send(user, message, "second_pillar_mandate_en") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, EmailType.SECOND_PILLAR_MANDATE, mandrillResponse.status, mandate)
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
    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, locale)

    then:
    1 * emailService.newMandrillMessage(user.email, "third_pillar_payment_reminder_mandate_en", mergeVars, tags, !null) >> message
    1 * emailService.send(user, message, "third_pillar_payment_reminder_mandate_en", sendAt) >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE, mandrillResponse.status, mandate)
  }

  def "schedule third pillar suggest second pillar email"() {
    given:
    def user = sampleUser().build()
    def mandate = thirdPillarMandate()
    PillarSuggestion pillarSuggestion = Mock()
    pillarSuggestion.isSuggestPillar() >> true
    def message = new MandrillMessage()
    def mergeVars = [fname: user.firstName, lname: user.lastName]
    def tags = ["pillar_3.1", "suggest_2"]
    def locale = Locale.ENGLISH
    def sendAt = now.plus(3, DAYS)
    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    when:
    mandateEmailService.scheduleThirdPillarSuggestSecondEmail(user, mandate, pillarSuggestion, locale)

    then:
    1 * emailService.newMandrillMessage(user.email, "third_pillar_suggest_second_en", mergeVars, tags, null) >> message
    1 * emailService.send(user, message, "third_pillar_suggest_second_en", sendAt) >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, EmailType.THIRD_PILLAR_SUGGEST_SECOND, mandrillResponse.status)
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
    def mandrillResponse1 = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }
    def mandrillResponse2 = new MandrillMessageStatus().tap {
      _id = "234"
      status = "sent"
    }
    1 * emailService.newMandrillMessage(user.email, "third_pillar_payment_reminder_mandate_en", mergeVars, ["pillar_3.1", "reminder"], !null) >> paymentReminder
    1 * emailService.send(user, paymentReminder, "third_pillar_payment_reminder_mandate_en", now.plus(1, HOURS)) >> Optional.of(mandrillResponse1)
    1 * emailPersistenceService.save(user, mandrillResponse1.id, EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE, mandrillResponse1.status, mandate)

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, locale)

    then:
    callCount * emailService.newMandrillMessage(user.email, "third_pillar_suggest_second_en", mergeVars, ["pillar_3.1", "suggest_2"], null) >> suggestSecond
    callCount * emailService.send(user, suggestSecond, "third_pillar_suggest_second_en", now.plus(3, DAYS)) >> Optional.of(mandrillResponse2)
    callCount * emailPersistenceService.save(user, mandrillResponse2.id, EmailType.THIRD_PILLAR_SUGGEST_SECOND, mandrillResponse2.status)

    where:
    suggestPillar | callCount
    true          | 1
    false         | 0
  }

  def "Sends two third pillar emails"() {
    given:
    def user = sampleUser().build()
    def conversion = notConverted()
    def contactDetails = contactDetailsFixture()
    def pillarSuggestion = new PillarSuggestion(2, user, contactDetails, conversion)
    emailService.newMandrillMessage(*_) >> new MandrillMessage()
    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = UUID.randomUUID().toString()
      status = "sent"
    }

    when:
    mandateEmailService.sendMandate(user, thirdPillarMandate(), pillarSuggestion, Locale.ENGLISH)

    then:
    2 * emailService.send(*_) >> Optional.of(mandrillResponse)
  }

  def "Send second pillar payment rate mandate email"() {
    given:
    def user = sampleUser().build()
    def conversion = notConverted()
    def contactDetails = contactDetailsFixture()
    def mandate = sampleMandateWithPaymentRate()
    def pillarSuggestion = new PillarSuggestion(2, user, contactDetails, conversion)
    def message = new MandrillMessage()
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    def samplePaymentRates = new PaymentRates(
        2, 6
    )

    def mergeVars = [
        fname                     : user.firstName,
        lname                     : user.lastName,
        suggestMembership         : false,
        paymentRateFulfillmentDate: "01.01.2022",
        newPaymentRate            : samplePaymentRates.pending.get(),
        oldPaymentRate            : samplePaymentRates.current,
        suggestThirdPillar        : true
    ]

    def tags = ["mandate", "pillar_2", "suggest_3"]

    authenticationHolder.getAuthenticatedPerson() >> authenticatedPerson
    mandateDeadlinesService.getDeadlines(mandate.createdDate) >> sampleDeadlines()
    secondPillarPaymentRateService.getPaymentRates(authenticatedPerson) >> samplePaymentRates
    mandateDeadlinesService.getDeadlines() >> sampleDeadlines()
    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)

    then:
    1 * emailService.newMandrillMessage(user.email, "second_pillar_payment_rate_en", mergeVars, tags, !null) >> message
    1 * emailService.send(user, message, "second_pillar_payment_rate_en") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, EmailType.SECOND_PILLAR_PAYMENT_RATE, mandrillResponse.status, mandate)
  }

  def "Send second pillar payment rate mandate email, error when no pending rate"() {
    given:
    def user = sampleUser().build()
    def conversion = notConverted()
    def contactDetails = contactDetailsFixture()
    def mandate = sampleMandateWithPaymentRate()
    def pillarSuggestion = new PillarSuggestion(2, user, contactDetails, conversion)
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    def samplePaymentRates = new PaymentRates(
        2, null
    )

    authenticationHolder.getAuthenticatedPerson() >> authenticatedPerson
    mandateDeadlinesService.getDeadlines(mandate.createdDate) >> sampleDeadlines()
    secondPillarPaymentRateService.getPaymentRates(authenticatedPerson) >> samplePaymentRates
    mandateDeadlinesService.getDeadlines() >> sampleDeadlines()

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)

    then:
    thrown(NoSuchElementException)
  }

  def "does not send email when already sent today"() {
    given:
    def user = sampleUser().build()
    def conversion = fullyConverted()
    def contactDetails = contactDetailsFixture()
    def mandate = thirdPillarMandate()
    def pillarSuggestion = new PillarSuggestion(3, user, contactDetails, conversion)
    emailPersistenceService.hasEmailsToday(user, EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE) >> true

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)

    then:
    0 * emailService.send(*_)
  }

}
