package ee.tuleva.onboarding.mandate.email

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.auth.principal.AuthenticationHolder
import ee.tuleva.onboarding.deadline.MandateDeadlinesService
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService
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
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.*
import static ee.tuleva.onboarding.paymentrate.PaymentRatesFixture.samplePaymentRates
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
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)
    def message = new MandrillMessage()
    def mergeVars = [
        fname              : user.firstName,
        lname              : user.lastName,
        transferDate       : "03.05.2021",
        suggestPaymentRate : pillarSuggestion.suggestPaymentRate,
        suggestSecondPillar: pillarSuggestion.suggestSecondPillar,
        suggestThirdPillar : pillarSuggestion.suggestThirdPillar,
        suggestMembership  : pillarSuggestion.suggestMembership,
    ]
    def tags = ["mandate", "pillar_2", "suggest_payment_rate", "suggest_3"]
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
    1 * emailPersistenceService.save(user, mandrillResponse.id, SECOND_PILLAR_MANDATE, mandrillResponse.status, mandate)
  }

  def "mandate tagging for 2nd pillar mandates"() {
    given:
    def pillarSuggestion = Mock(PillarSuggestion)
    pillarSuggestion.isSuggestThirdPillar() >> suggestThirdPillar
    pillarSuggestion.isSuggestMembership() >> suggestMember
    pillarSuggestion.isSuggestPaymentRate() >> suggestPaymentRate


    when:
    def tags = mandateEmailService.getSecondPillarMandateTags(pillarSuggestion)

    then:
    tags == expectedTags

    where:
    suggestPaymentRate | suggestThirdPillar | suggestMember || expectedTags
    false              | false              | false         || ["mandate", "pillar_2"]
    true               | false              | false         || ["mandate", "pillar_2", "suggest_payment_rate"]
    true               | true               | false         || ["mandate", "pillar_2", "suggest_payment_rate", "suggest_3"]
    true               | true               | true          || ["mandate", "pillar_2", "suggest_payment_rate", "suggest_3", "suggest_member"]
  }

  def "schedule third pillar payment reminder email"() {
    given:
    def user = sampleUser().build()
    def conversion = fullyConverted()
    def contactDetails = contactDetailsFixture()
    def mandate = thirdPillarMandate()
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)
    def message = new MandrillMessage()
    def mergeVars = [fname: user.firstName, lname: user.lastName]
    def tags = ["pillar_3.1", "reminder"]
    def locale = Locale.ENGLISH
    def sendAt = now.plus(1, HOURS)
    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    emailPersistenceService.hasEmailsFor(mandate) >> false


    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, locale)

    then:
    1 * emailService.newMandrillMessage(user.email, "third_pillar_payment_reminder_mandate_en", mergeVars, tags, !null) >> message
    1 * emailService.send(user, message, "third_pillar_payment_reminder_mandate_en", sendAt) >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, THIRD_PILLAR_PAYMENT_REMINDER_MANDATE, mandrillResponse.status, mandate)
  }

  def "schedule third pillar suggest second pillar email"() {
    given:
    def user = sampleUser().build()
    def mandate = thirdPillarMandate()
    PillarSuggestion pillarSuggestion = Mock()
    pillarSuggestion.isSuggestSecondPillar() >> true
    def message = new MandrillMessage()
    def mergeVars = [fname: user.firstName, lname: user.lastName]
    def tags = ["pillar_3.1", "suggest_2"]
    def locale = Locale.ENGLISH
    def sendAt = now.plus(3, DAYS)
    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    emailPersistenceService.hasEmailsFor(mandate) >> false


    when:
    mandateEmailService.scheduleThirdPillarSuggestSecondEmail(user, mandate, pillarSuggestion, locale)

    then:
    1 * emailService.newMandrillMessage(user.email, "third_pillar_suggest_second_en", mergeVars, tags, null) >> message
    1 * emailService.send(user, message, "third_pillar_suggest_second_en", sendAt) >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, THIRD_PILLAR_SUGGEST_SECOND, mandrillResponse.status)
  }

  def "Send third pillar suggest second pillar email"() {
    given:
    def user = sampleUser().build()

    PillarSuggestion pillarSuggestion = Mock()
    pillarSuggestion.isSuggestSecondPillar() >> suggestPillar

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
    1 * emailPersistenceService.save(user, mandrillResponse1.id, THIRD_PILLAR_PAYMENT_REMINDER_MANDATE, mandrillResponse1.status, mandate)

    emailPersistenceService.hasEmailsFor(mandate) >> false

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, locale)

    then:
    callCount * emailService.newMandrillMessage(user.email, "third_pillar_suggest_second_en", mergeVars, ["pillar_3.1", "suggest_2"], null) >> suggestSecond
    callCount * emailService.send(user, suggestSecond, "third_pillar_suggest_second_en", now.plus(3, DAYS)) >> Optional.of(mandrillResponse2)
    callCount * emailPersistenceService.save(user, mandrillResponse2.id, THIRD_PILLAR_SUGGEST_SECOND, mandrillResponse2.status)

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
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)
    def mandate = thirdPillarMandate()
    emailService.newMandrillMessage(*_) >> new MandrillMessage()
    emailPersistenceService.hasEmailsFor(mandate) >> false

    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = UUID.randomUUID().toString()
      status = "sent"
    }

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)

    then:
    2 * emailService.send(*_) >> Optional.of(mandrillResponse)
  }

  def "Send second pillar payment rate mandate email"() {
    given:
    def user = sampleUser().build()
    def conversion = notConverted()
    def contactDetails = contactDetailsFixture()
    def mandate = sampleMandateWithPaymentRate()
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)
    def message = new MandrillMessage()
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    def samplePaymentRates = new PaymentRates(
        2, 6
    )

    def mergeVars = [
        fname                     : user.firstName,
        lname                     : user.lastName,
        paymentRateFulfillmentDate: "01.01.2022",
        newPaymentRate            : samplePaymentRates.pending.get(),
        oldPaymentRate            : samplePaymentRates.current,
        decreased                 : false,  // 6 > 2, so not decreased
        increased                 : true,   // 6 > 2, so increased
        suggestPaymentRate        : pillarSuggestion.suggestPaymentRate,
        suggestSecondPillar       : pillarSuggestion.suggestSecondPillar,
        suggestThirdPillar        : pillarSuggestion.suggestThirdPillar,
        suggestMembership         : pillarSuggestion.suggestMembership,
    ]

    authenticationHolder.getAuthenticatedPerson() >> authenticatedPerson
    mandateDeadlinesService.getDeadlines(mandate.createdDate) >> sampleDeadlines()
    secondPillarPaymentRateService.getPaymentRates(authenticatedPerson) >> samplePaymentRates
    mandateDeadlinesService.getDeadlines() >> sampleDeadlines()
    emailPersistenceService.hasEmailsFor(mandate) >> false

    def tags = ["mandate", "pillar_2", "suggest_payment_rate", "suggest_3"]

    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)

    then:
    1 * emailService.newMandrillMessage(user.email, "second_pillar_payment_rate_en", mergeVars, tags, !null) >> message
    1 * emailService.send(user, message, "second_pillar_payment_rate_en") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, SECOND_PILLAR_PAYMENT_RATE, mandrillResponse.status, mandate)
  }

  def "Send second pillar payment rate mandate email with decreased rate"() {
    given:
    def user = sampleUser().build()
    def conversion = notConverted()
    def contactDetails = contactDetailsFixture()
    def mandate = sampleMandateWithPaymentRate()
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)
    def message = new MandrillMessage()
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    def samplePaymentRates = new PaymentRates(
        4, 2  // Decreasing from 4 to 2
    )

    def mergeVars = [
        fname                     : user.firstName,
        lname                     : user.lastName,
        paymentRateFulfillmentDate: "01.01.2022",
        newPaymentRate            : samplePaymentRates.pending.get(),
        oldPaymentRate            : samplePaymentRates.current,
        decreased                 : true,   // 2 < 4 and 2 == 2, so decreased
        increased                 : false,  // not increased
        suggestPaymentRate        : pillarSuggestion.suggestPaymentRate,
        suggestSecondPillar       : pillarSuggestion.suggestSecondPillar,
        suggestThirdPillar        : pillarSuggestion.suggestThirdPillar,
        suggestMembership         : pillarSuggestion.suggestMembership,
    ]

    authenticationHolder.getAuthenticatedPerson() >> authenticatedPerson
    mandateDeadlinesService.getDeadlines(mandate.createdDate) >> sampleDeadlines()
    secondPillarPaymentRateService.getPaymentRates(authenticatedPerson) >> samplePaymentRates
    mandateDeadlinesService.getDeadlines() >> sampleDeadlines()
    emailPersistenceService.hasEmailsFor(mandate) >> false

    def tags = ["mandate", "pillar_2", "suggest_payment_rate", "suggest_3"]

    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)

    then:
    1 * emailService.newMandrillMessage(user.email, "second_pillar_payment_rate_en", mergeVars, tags, !null) >> message
    1 * emailService.send(user, message, "second_pillar_payment_rate_en") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, SECOND_PILLAR_PAYMENT_RATE, mandrillResponse.status, mandate)
  }

  def "Send second pillar payment rate mandate email, error when no pending rate"() {
    given:
    def user = sampleUser().build()
    def conversion = notConverted()
    def contactDetails = contactDetailsFixture()
    def mandate = sampleMandateWithPaymentRate()
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    def samplePaymentRates = new PaymentRates(
        2, null
    )

    authenticationHolder.getAuthenticatedPerson() >> authenticatedPerson
    mandateDeadlinesService.getDeadlines(mandate.createdDate) >> sampleDeadlines()
    secondPillarPaymentRateService.getPaymentRates(authenticatedPerson) >> samplePaymentRates
    mandateDeadlinesService.getDeadlines() >> sampleDeadlines()
    emailPersistenceService.hasEmailsFor(mandate) >> false


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
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)
    emailPersistenceService.hasEmailsFor(mandate) >> false
    emailPersistenceService.hasEmailsToday(user, THIRD_PILLAR_PAYMENT_REMINDER_MANDATE, mandate) >> true

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)

    then:
    0 * emailService.send(*_)
  }

  def "does not send email when email already present for mandate"() {
    given:
    def user = sampleUser().build()
    def conversion = fullyConverted()
    def contactDetails = contactDetailsFixture()
    def mandate = thirdPillarMandate()
    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)

    emailPersistenceService.hasEmailsFor(mandate) >> true

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)

    then:
    0 * emailService.send(*_)
  }

  def "isPaymentRateDecreased returns correct value for all rate combinations"() {
    expect:
    mandateEmailService.isPaymentRateDecreased(oldRate, newRate) == isPaymentRateDecreased

    where:
    oldRate | newRate || isPaymentRateDecreased
    2       | 2       || true
    2       | 4       || false
    2       | 6       || false
    4       | 2       || true
    4       | 4       || false
    4       | 6       || false
    6       | 2       || true
    6       | 4       || true
    6       | 6       || false
  }


}
