package ee.tuleva.onboarding.notification.email.auto


import ee.tuleva.onboarding.analytics.earlywithdrawals.AnalyticsEarlyWithdrawalsRepository
import ee.tuleva.onboarding.analytics.leavers.AnalyticsLeaversRepository
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService
import ee.tuleva.onboarding.notification.email.provider.MailchimpService
import ee.tuleva.onboarding.time.TestClockHolder
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

import static ee.tuleva.onboarding.analytics.earlywithdrawals.AnalyticsEarlyWithdrawalFixture.anEarlyWithdrawal
import static ee.tuleva.onboarding.analytics.leavers.AnalyticsLeaverFixture.leaverFixture
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_EARLY_WITHDRAWAL
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_LEAVERS
import static ee.tuleva.onboarding.notification.email.auto.EmailEvent.NEW_EARLY_WITHDRAWAL
import static ee.tuleva.onboarding.notification.email.auto.EmailEvent.NEW_LEAVER

class AutoEmailSenderSpec extends Specification {

  Clock clock = TestClockHolder.clock
  AnalyticsLeaversRepository leaversRepository = Spy()
  AnalyticsEarlyWithdrawalsRepository withdrawalsRepository = Spy()
  MailchimpService mailchimpService = Mock()
  EmailPersistenceService emailPersistenceService = Mock()
  AutoEmailSender autoEmailSender =
      new AutoEmailSender(clock, List.of(leaversRepository, withdrawalsRepository), mailchimpService, emailPersistenceService)

  def "sends leaver emails"() {
    given:
    def leaver = leaverFixture()
    def startDate = LocalDate.now(clock).withDayOfMonth(1)
    def endDate = startDate.withDayOfMonth(startDate.lengthOfMonth())
    leaversRepository.fetch(startDate, endDate) >> [leaver]
    withdrawalsRepository.fetch(_ as LocalDate, _ as LocalDate) >> []
    emailPersistenceService.getLastEmailSendDate(leaver, SECOND_PILLAR_LEAVERS) >> Optional.empty()

    when:
    autoEmailSender.sendMonthlyEmails()

    then:
    1 * mailchimpService.sendEvent(leaver.email(), NEW_LEAVER)
  }

  def "does not send duplicates"() {
    given:
    def leaver = leaverFixture()
    def startDate = LocalDate.now(clock).withDayOfMonth(1)
    def endDate = startDate.withDayOfMonth(startDate.lengthOfMonth())
    leaversRepository.fetch(startDate, endDate) >> [leaver]
    withdrawalsRepository.fetch(_ as LocalDate, _ as LocalDate) >> []
    emailPersistenceService.getLastEmailSendDate(leaver, SECOND_PILLAR_LEAVERS) >> Optional.of(Instant.now(clock))

    when:
    autoEmailSender.sendMonthlyEmails()

    then:
    0 * mailchimpService.sendEvent(leaver.email(), NEW_LEAVER)
  }

  def "sends withdrawal emails"() {
    given:
    def earlyWithdrawal = anEarlyWithdrawal()
    def startDate = LocalDate.now(clock).withDayOfMonth(1)
    def endDate = startDate.withDayOfMonth(startDate.lengthOfMonth())
    withdrawalsRepository.fetch(startDate, endDate) >> [earlyWithdrawal]
    leaversRepository.fetch(_ as LocalDate, _ as LocalDate) >> []
    emailPersistenceService.getLastEmailSendDate(earlyWithdrawal, SECOND_PILLAR_EARLY_WITHDRAWAL) >> Optional.empty()

    when:
    autoEmailSender.sendMonthlyEmails()

    then:
    1 * mailchimpService.sendEvent(earlyWithdrawal.email(), NEW_EARLY_WITHDRAWAL)
  }
}
