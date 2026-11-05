package ee.tuleva.onboarding.investment.transaction.export

import ee.tuleva.onboarding.event.EventLog
import ee.tuleva.onboarding.event.EventLogRepository
import ee.tuleva.onboarding.notification.OperationsNotificationService
import ee.tuleva.onboarding.notification.email.EmailDeliveryFailedEvent
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.web.client.RestClientException
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING

class CustodianOrderEmailFailureAlerterSpec extends Specification {

  OperationsNotificationService notificationService = Mock()
  EventLogRepository eventLogRepository = Mock()
  CustodianOrderEmailFailureAlerter alerter =
      new CustodianOrderEmailFailureAlerter(notificationService, eventLogRepository)

  Instant t0 = Instant.parse("2026-01-15T10:01:00Z")
  String subject = "TUV100 ostu-/müügikorraldused 15.01.2026"

  def "records an undelivered order email before posting the red alert, then marks it alerted: #eventType"() {
    given:
    eventLogRepository.existsByTypeAndPrincipal(recordedType, "msg_1") >> false

    when:
    alerter.onEmailDeliveryFailed(orderEmailFailure(eventType, "550 mailbox unavailable"))

    then:
    1 * eventLogRepository.save({ EventLog it ->
      it.type == recordedType &&
          it.principal == "msg_1" &&
          it.timestamp == t0 &&
          it.data == failureData(eventType, "550 mailbox unavailable", false)
    })

    then:
    1 * notificationService.sendMessage(
        "🔴 Order email NOT delivered: to=desk@example.com, subject=$subject\n" +
            "$eventType: 550 mailbox unavailable (id=msg_1)\n" +
            "The orders did not reach this address. Check with the desk before sending them again.",
        TRADING, ERROR)

    then:
    1 * eventLogRepository.save({ EventLog it ->
      it.type == recordedType && it.data == failureData(eventType, "550 mailbox unavailable", true)
    })

    where:
    eventType     | recordedType
    "hard_bounce" | "TRADE_ORDER_EMAIL_HARD_BOUNCE"
    "soft_bounce" | "TRADE_ORDER_EMAIL_SOFT_BOUNCE"
    "reject"      | "TRADE_ORDER_EMAIL_REJECT"
  }

  def "posts an amber alert when an order email is delayed"() {
    given:
    eventLogRepository.existsByTypeAndPrincipal("TRADE_ORDER_EMAIL_DEFERRAL", "msg_1") >> false

    when:
    alerter.onEmailDeliveryFailed(orderEmailFailure("deferral", "450 4.7.25 try again later"))

    then:
    1 * eventLogRepository.save({ EventLog it -> it.type == "TRADE_ORDER_EMAIL_DEFERRAL" && it.data.alertPosted == false })

    then:
    1 * notificationService.sendMessage(
        "🟠 Order email delayed: to=desk@example.com, subject=$subject\n" +
            "deferral: 450 4.7.25 try again later (id=msg_1)\n" +
            "Mandrill keeps trying. If the desk hasn't confirmed the orders before the cut-off, check with the desk.",
        TRADING, WARNING)

    then:
    1 * eventLogRepository.save({ EventLog it -> it.type == "TRADE_ORDER_EMAIL_DEFERRAL" && it.data.alertPosted == true })
  }

  def "alerts once per message and failure type"() {
    given:
    eventLogRepository.existsByTypeAndPrincipal("TRADE_ORDER_EMAIL_DEFERRAL", "msg_1") >> true

    when:
    alerter.onEmailDeliveryFailed(orderEmailFailure("deferral", "450 4.7.25 try again later"))

    then:
    0 * eventLogRepository.save(_)
    0 * notificationService.sendMessage(_, _, _)
  }

  def "ignores delivery failures of emails that are not order emails"() {
    given:
    def event = new EmailDeliveryFailedEvent(
        "trustee@seb.ee", "TKF100 NAV arvutamine 12.06.2026", "hard_bounce", "550", "msg_2", t0, [])

    when:
    alerter.onEmailDeliveryFailed(event)

    then:
    0 * eventLogRepository.existsByTypeAndPrincipal(_, _)
    0 * eventLogRepository.save(_)
    0 * notificationService.sendMessage(_, _, _)
  }

  def "keeps the failure recorded as not alerted when Slack cannot be reached, so the retry run posts it"() {
    given:
    eventLogRepository.existsByTypeAndPrincipal(_, _) >> false
    notificationService.sendMessage(_, _, _) >> { throw new RestClientException("Slack is down") }

    when:
    alerter.onEmailDeliveryFailed(orderEmailFailure("hard_bounce", "550 mailbox unavailable"))

    then:
    noExceptionThrown()
    1 * eventLogRepository.save({ EventLog it -> it.data.alertPosted == false })
    0 * eventLogRepository.save({ EventLog it -> it.data.alertPosted == true })
  }

  def "the retry run posts a failure Slack missed and marks it alerted"() {
    given:
    eventLogRepository.findAllByTypeIn(_) >> [recordedFailure("hard_bounce", false)]

    when:
    alerter.alertFailuresSlackMissed()

    then:
    1 * notificationService.sendMessage(
        "🔴 Order email NOT delivered: to=desk@example.com, subject=$subject\n" +
            "hard_bounce: 550 mailbox unavailable (id=msg_1)\n" +
            "The orders did not reach this address. Check with the desk before sending them again.",
        TRADING, ERROR)

    then:
    1 * eventLogRepository.save({ EventLog it ->
      it.type == "TRADE_ORDER_EMAIL_HARD_BOUNCE" &&
          it.principal == "msg_1" &&
          it.data == failureData("hard_bounce", "550 mailbox unavailable", true)
    })
  }

  def "the retry run does not post a failure again once it was alerted"() {
    given:
    eventLogRepository.findAllByTypeIn(_) >> [recordedFailure("hard_bounce", true)]

    when:
    alerter.alertFailuresSlackMissed()

    then:
    0 * notificationService.sendMessage(_, _, _)
    0 * eventLogRepository.save(_)
  }

  def "the retry run keeps a failure not alerted while Slack is still down, and still posts the others"() {
    given:
    def stillDown = recordedFailure("hard_bounce", false)
    def other = recordedFailure("reject", false)
    other.principal = "msg_2"
    eventLogRepository.findAllByTypeIn(_) >> [stillDown, other]
    notificationService.sendMessage({ it.contains("id=msg_1") }, _, _) >> { throw new RestClientException("Slack is down") }

    when:
    alerter.alertFailuresSlackMissed()

    then:
    noExceptionThrown()
    1 * eventLogRepository.save({ EventLog it -> it.principal == "msg_2" && it.data.alertPosted == true })
    0 * eventLogRepository.save({ EventLog it -> it.principal == "msg_1" })
  }

  def "alerts before any other delivery failure listener, so a failing one cannot swallow the alert"() {
    given:
    eventLogRepository.existsByTypeAndPrincipal(_, _) >> false
    def context = new AnnotationConfigApplicationContext()
    context.registerBean(FailingDeliveryFailureListener)
    context.registerBean(CustodianOrderEmailFailureAlerter, { -> alerter } as java.util.function.Supplier)
    context.refresh()

    when:
    context.publishEvent(orderEmailFailure("hard_bounce", "550 mailbox unavailable"))

    then:
    thrown(IllegalStateException)
    1 * notificationService.sendMessage({ it.startsWith("🔴 Order email NOT delivered") }, TRADING, ERROR)

    cleanup:
    context.close()
  }

  static class FailingDeliveryFailureListener {
    @EventListener
    void onEmailDeliveryFailed(EmailDeliveryFailedEvent event) {
      throw new IllegalStateException("Another listener failed")
    }
  }

  private EmailDeliveryFailedEvent orderEmailFailure(String eventType, String reason) {
    new EmailDeliveryFailedEvent(
        "desk@example.com", subject, eventType, reason, "msg_1", t0, ["trade-order"])
  }

  private Map failureData(String eventType, String reason, boolean alertPosted) {
    [recipient  : "desk@example.com",
     subject    : subject,
     eventType  : eventType,
     reason     : reason,
     alertPosted: alertPosted]
  }

  private EventLog recordedFailure(String eventType, boolean alertPosted) {
    EventLog.builder()
        .type("TRADE_ORDER_EMAIL_" + eventType.toUpperCase())
        .principal("msg_1")
        .timestamp(t0)
        .data(failureData(eventType, "550 mailbox unavailable", alertPosted))
        .build()
  }
}
