package ee.tuleva.onboarding.notification.email


import ee.tuleva.onboarding.analytics.AnalyticsLeaversRepository
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService
import ee.tuleva.onboarding.time.TestClockHolder
import spock.lang.Specification

import java.time.Clock
import java.time.LocalDate

import static ee.tuleva.onboarding.analytics.AnalyticsLeaverFixture.leaverFixture
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_LEAVERS

class AutoEmailSenderSpec extends Specification {

  Clock clock = TestClockHolder.clock
  AnalyticsLeaversRepository leaversRepository = Mock()
  MailchimpService mailchimpService = Mock()
  EmailPersistenceService emailPersistenceService = Mock()
  AutoEmailSender autoEmailSender = new AutoEmailSender(clock, leaversRepository, mailchimpService, emailPersistenceService)

  def "sends leaver emails"() {
    given:
    def leaver = leaverFixture()
    def startDate = LocalDate.now(clock).withDayOfMonth(1)
    def endDate = startDate.withDayOfMonth(startDate.lengthOfMonth())
    leaversRepository.fetchLeavers(startDate, endDate) >> [leaver]

    when:
    autoEmailSender.sendMonthlyLeaverEmail()

    then:
    1 * mailchimpService.sendEvent(leaver.email(), "new_leaver")
  }

  def "does not send duplicates"() {
    given:
    def leaver = leaverFixture()
    def startDate = LocalDate.now(clock).withDayOfMonth(1)
    def endDate = startDate.withDayOfMonth(startDate.lengthOfMonth())
    leaversRepository.fetchLeavers(startDate, endDate) >> [leaver]
    emailPersistenceService.hasEmailsToday(leaver, SECOND_PILLAR_LEAVERS) >> true

    when:
    autoEmailSender.sendMonthlyLeaverEmail()

    then:
    0 * mailchimpService.sendEvent(leaver.email(), "new_leaver")
  }
}
