package ee.tuleva.onboarding.savings.fund.nav

import ee.tuleva.onboarding.event.EventLog
import ee.tuleva.onboarding.event.EventLogRepository
import ee.tuleva.onboarding.notification.OperationsNotificationService
import ee.tuleva.onboarding.notification.email.EmailDeliveryFailedEvent
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS

class TrusteeEmailFailureAlerterSpec extends Specification {

  OperationsNotificationService notificationService = Mock()
  EventLogRepository eventLogRepository = Mock()
  TrusteeEmailFailureAlerter alerter = new TrusteeEmailFailureAlerter(notificationService, eventLogRepository)

  Instant t0 = Instant.parse("2026-06-15T15:20:00Z")

  def "alerts and records the failure when it has not been alerted before"() {
    given:
    eventLogRepository.existsByTypeAndPrincipal("DEFERRAL", "msg_1") >> false

    when:
    alerter.onEmailDeliveryFailed(deferralEvent("msg_1"))

    then:
    1 * eventLogRepository.save({ EventLog it ->
      it.type == "DEFERRAL" &&
          it.principal == "msg_1" &&
          it.timestamp == t0 &&
          it.data.recipient == "trustee@seb.ee"
    })
    1 * notificationService.sendMessage({ String message ->
      message.contains("trustee@seb.ee") &&
          message.contains("deferral") &&
          message.contains("TKF100 NAV arvutamine 12.06.2026") &&
          message.contains("450 4.7.25")
    }, SAVINGS)
  }

  def "suppresses the alert when the same failure was already recorded"() {
    given:
    eventLogRepository.existsByTypeAndPrincipal("DEFERRAL", "msg_1") >> true

    when:
    alerter.onEmailDeliveryFailed(deferralEvent("msg_1"))

    then:
    0 * eventLogRepository.save(_)
    0 * notificationService.sendMessage(_, _)
  }

  def "ignores delivery failures for non-trustee recipients"() {
    given:
    def event = new EmailDeliveryFailedEvent(
        "saver@example.com", "Some subject", "hard_bounce", "550 mailbox unavailable", "msg_2", t0)

    when:
    alerter.onEmailDeliveryFailed(event)

    then:
    0 * eventLogRepository.existsByTypeAndPrincipal(_, _)
    0 * eventLogRepository.save(_)
    0 * notificationService.sendMessage(_, _)
  }

  private EmailDeliveryFailedEvent deferralEvent(String messageId) {
    new EmailDeliveryFailedEvent(
        "trustee@seb.ee",
        "TKF100 NAV arvutamine 12.06.2026",
        "deferral",
        "450 4.7.25 Client host rejected: cannot find your hostname",
        messageId,
        t0)
  }
}
