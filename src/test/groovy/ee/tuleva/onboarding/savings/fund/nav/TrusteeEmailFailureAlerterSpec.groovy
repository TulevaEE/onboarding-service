package ee.tuleva.onboarding.savings.fund.nav

import ee.tuleva.onboarding.notification.OperationsNotificationService
import ee.tuleva.onboarding.notification.email.EmailDeliveryFailedEvent
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS

class TrusteeEmailFailureAlerterSpec extends Specification {

  OperationsNotificationService notificationService = Mock()
  TrusteeEmailFailureAlerter alerter = new TrusteeEmailFailureAlerter(notificationService)

  def "alerts the savings channel when a trustee email delivery fails"() {
    given:
    def event = new EmailDeliveryFailedEvent(
        "trustee@seb.ee",
        "TKF100 NAV arvutamine 12.06.2026",
        "deferral",
        "450 4.7.25 Client host rejected: cannot find your hostname",
        "mandrill_msg_123",
        Instant.ofEpochSecond(1699900000L))

    when:
    alerter.onEmailDeliveryFailed(event)

    then:
    1 * notificationService.sendMessage({ String message ->
      message.contains("trustee@seb.ee") &&
          message.contains("deferral") &&
          message.contains("TKF100 NAV arvutamine 12.06.2026") &&
          message.contains("450 4.7.25")
    }, SAVINGS)
  }

  def "ignores delivery failures for non-trustee recipients"() {
    given:
    def event = new EmailDeliveryFailedEvent(
        "saver@example.com",
        "Some subject",
        "hard_bounce",
        "550 mailbox unavailable",
        "mandrill_msg_456",
        Instant.ofEpochSecond(1699900000L))

    when:
    alerter.onEmailDeliveryFailed(event)

    then:
    0 * notificationService.sendMessage(_, _)
  }
}
