package ee.tuleva.onboarding.notification.email


import ee.tuleva.onboarding.analytics.AnalyticsLeaversRepository
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService
import ee.tuleva.onboarding.time.TestClockHolder
import spock.lang.Specification

import java.time.Clock
import java.time.LocalDate

import static ee.tuleva.onboarding.analytics.AnalyticsLeaverFixture.leaverFixture

class AutoEmailSenderSpec extends Specification {

  Clock clock = TestClockHolder.clock
  AnalyticsLeaversRepository leaversRepository = Mock()
  MailchimpService mailchimpService = Mock()
  EmailPersistenceService emailPersistenceService = Mock()
  AutoEmailSender autoEmailSender = new AutoEmailSender(clock, leaversRepository, mailchimpService, emailPersistenceService)

  def "sends monthly leaver emails"() {
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
}
