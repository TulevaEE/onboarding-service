package ee.tuleva.onboarding.investment.transaction.export

import ee.tuleva.onboarding.event.EventLog
import ee.tuleva.onboarding.event.EventLogRepository
import ee.tuleva.onboarding.notification.OperationsNotificationService
import ee.tuleva.onboarding.notification.email.EmailDeliveryFailedEvent
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.web.client.RestClientException
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING
import static java.time.ZoneOffset.UTC

class CustodianOrderEmailFailureAlerterSpec extends Specification {

  OperationsNotificationService notificationService = Mock()
  EventLogRepository eventLogRepository = Mock()

  Instant t0 = Instant.parse("2026-01-15T10:01:00Z")
  Instant aMinuteLater = t0.plus(Duration.ofMinutes(1))
  String subject = "TUV100 ostu-/müügikorraldused 15.01.2026"
  String bounce = "550 mailbox unavailable"

  CustodianOrderEmailFailureAlerter alerter = alerterAt(aMinuteLater)

  def "records an undelivered order email for the next run to post, without calling Slack: #eventType"() {
    given:
    eventLogRepository.existsByTypeAndPrincipal(recordedType, "msg_1") >> false

    when:
    alerter.onEmailDeliveryFailed(orderEmailFailure(eventType, bounce))

    then:
    1 * eventLogRepository.save({ EventLog it ->
      it.type == recordedType &&
          it.principal == "msg_1" &&
          it.timestamp == t0 &&
          it.data == notYetPostedData(eventType, bounce)
    })
    0 * notificationService._

    where:
    eventType     | recordedType
    "hard_bounce" | "TRADE_ORDER_EMAIL_HARD_BOUNCE"
    "soft_bounce" | "TRADE_ORDER_EMAIL_SOFT_BOUNCE"
    "reject"      | "TRADE_ORDER_EMAIL_REJECT"
    "deferral"    | "TRADE_ORDER_EMAIL_DEFERRAL"
  }

  def "records once per message and failure type"() {
    given:
    eventLogRepository.existsByTypeAndPrincipal("TRADE_ORDER_EMAIL_DEFERRAL", "msg_1") >> true

    when:
    alerter.onEmailDeliveryFailed(orderEmailFailure("deferral", "450 4.7.25 try again later"))

    then:
    0 * eventLogRepository.save(_)
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
  }

  def "records before any other delivery failure listener, so a failing one cannot stop the record"() {
    given:
    eventLogRepository.existsByTypeAndPrincipal(_, _) >> false
    def context = new AnnotationConfigApplicationContext()
    context.registerBean(FailingDeliveryFailureListener)
    context.registerBean(CustodianOrderEmailFailureAlerter, { -> alerter } as java.util.function.Supplier)
    context.refresh()

    when:
    context.publishEvent(orderEmailFailure("hard_bounce", bounce))

    then:
    thrown(IllegalStateException)
    1 * eventLogRepository.save({ EventLog it -> it.type == "TRADE_ORDER_EMAIL_HARD_BOUNCE" })

    cleanup:
    context.close()
  }

  def "the run reads order email failures of the last two days, oldest first"() {
    when:
    alerter.postFailuresNotYetPosted()

    then:
    1 * eventLogRepository.findAllByTypeInAndTimestampGreaterThanEqualOrderByTimestamp(
        ["TRADE_ORDER_EMAIL_HARD_BOUNCE", "TRADE_ORDER_EMAIL_SOFT_BOUNCE",
         "TRADE_ORDER_EMAIL_REJECT", "TRADE_ORDER_EMAIL_DEFERRAL"],
        aMinuteLater.minus(Duration.ofHours(48))) >> []
  }

  def "the run posts a recorded failure in red and then marks it posted: #eventType"() {
    given:
    rowsRead(notYetPostedFailure(eventType))

    when:
    alerter.postFailuresNotYetPosted()

    then:
    1 * notificationService.sendMessage(
        "🔴 Order email NOT delivered: to=desk@example.com, subject=$subject\n" +
            "$eventType: 550 mailbox unavailable (id=msg_1)\n" +
            "The orders did not reach this address. Check with the desk before sending them again.",
        TRADING, ERROR)

    then:
    1 * eventLogRepository.save({ EventLog it ->
      it.principal == "msg_1" && it.data == postedData(eventType, bounce)
    })

    where:
    eventType << ["hard_bounce", "soft_bounce", "reject"]
  }

  def "the run posts a recorded deferral in amber"() {
    given:
    rowsRead(notYetPostedFailure("deferral", "450 4.7.25 try again later", t0))

    when:
    alerter.postFailuresNotYetPosted()

    then:
    1 * notificationService.sendMessage(
        "🟠 Order email delayed: to=desk@example.com, subject=$subject\n" +
            "deferral: 450 4.7.25 try again later (id=msg_1)\n" +
            "Mandrill keeps trying. If the desk hasn't confirmed the orders before the cut-off, check with the desk.",
        TRADING, WARNING)

    then:
    1 * eventLogRepository.save({ EventLog it -> it.data == postedData("deferral", "450 4.7.25 try again later") })
  }

  def "a post that goes out more than five minutes after the event says when the event happened"() {
    given:
    alerter = alerterAt(t0.plus(Duration.ofHours(3)))
    rowsRead(notYetPostedFailure("deferral", "450 4.7.25 try again later", t0))

    when:
    alerter.postFailuresNotYetPosted()

    then:
    1 * notificationService.sendMessage(
        "🟠 Order email delayed: to=desk@example.com, subject=$subject\n" +
            "deferral at 15.01.2026 12:01: 450 4.7.25 try again later (id=msg_1)\n" +
            "Mandrill keeps trying. If the desk hasn't confirmed the orders before the cut-off, check with the desk.",
        TRADING, WARNING)
  }

  def "the run does not post a failure that was already posted or given up"() {
    given:
    rowsRead(postedFailure("hard_bounce"), givenUpFailure("reject"))

    when:
    alerter.postFailuresNotYetPosted()

    then:
    0 * notificationService._
    0 * eventLogRepository.save(_)
  }

  def "a failure Slack does not take stays not posted for the next run, and the others are still posted"() {
    given:
    rowsRead(notYetPostedFailure("hard_bounce"), notYetPostedFailure("reject", "msg_2"))
    notificationService.sendMessage({ it.contains("id=msg_1") }, _, _) >> { throw new RestClientException("Slack is down") }

    when:
    alerter.postFailuresNotYetPosted()

    then:
    noExceptionThrown()
    1 * eventLogRepository.save({ EventLog it -> it.principal == "msg_2" && it.data == postedData("reject", bounce) })
    0 * eventLogRepository.save({ EventLog it -> it.principal == "msg_1" })
  }

  def "a row that cannot be read does not stop the run"() {
    given:
    def unreadable = notYetPostedFailure("hard_bounce")
    unreadable.data = [eventType: 42, alert: "NOT_POSTED"]
    rowsRead(unreadable, notYetPostedFailure("reject", "msg_2"))

    when:
    alerter.postFailuresNotYetPosted()

    then:
    noExceptionThrown()
    1 * notificationService.sendMessage({ it.contains("id=msg_2") }, TRADING, ERROR)
    1 * eventLogRepository.save({ EventLog it -> it.principal == "msg_2" })
  }

  def "a failure still not posted at the end of the 24 hour retry window is given up instead of posted"() {
    given:
    alerter = alerterAt(t0.plus(Duration.ofHours(24)).plusSeconds(1))
    rowsRead(notYetPostedFailure("hard_bounce"))

    when:
    alerter.postFailuresNotYetPosted()

    then:
    0 * notificationService._
    1 * eventLogRepository.save({ EventLog it -> it.data == givenUpData("hard_bounce") })
  }

  def "a run posts at most four failures, so four hanging Slack posts still end inside the 5 minute lock"() {
    given:
    rowsRead((1..5).collect { notYetPostedFailure("hard_bounce", "msg_$it") } as EventLog[])

    when:
    alerter.postFailuresNotYetPosted()

    then:
    4 * notificationService.sendMessage(_, TRADING, ERROR)
    0 * notificationService.sendMessage({ it.contains("id=msg_5") }, _, _)
  }

  static class FailingDeliveryFailureListener {
    @EventListener
    void onEmailDeliveryFailed(EmailDeliveryFailedEvent event) {
      throw new IllegalStateException("Another listener failed")
    }
  }

  private CustodianOrderEmailFailureAlerter alerterAt(Instant now) {
    new CustodianOrderEmailFailureAlerter(notificationService, eventLogRepository, Clock.fixed(now, UTC))
  }

  private void rowsRead(EventLog... rows) {
    eventLogRepository.findAllByTypeInAndTimestampGreaterThanEqualOrderByTimestamp(_, _) >> rows.toList()
  }

  private EmailDeliveryFailedEvent orderEmailFailure(String eventType, String reason) {
    new EmailDeliveryFailedEvent(
        "desk@example.com", subject, eventType, reason, "msg_1", t0, ["trade-order"])
  }

  private Map notYetPostedData(String eventType, String reason) {
    failureData(eventType, reason) + [alert: "NOT_POSTED"]
  }

  private Map postedData(String eventType, String reason) {
    failureData(eventType, reason) + [alert: "POSTED"]
  }

  private Map givenUpData(String eventType) {
    failureData(eventType, bounce) + [alert: "GIVEN_UP"]
  }

  private Map failureData(String eventType, String reason) {
    [recipient: "desk@example.com",
     subject  : subject,
     eventType: eventType,
     reason   : reason]
  }

  private EventLog notYetPostedFailure(String eventType) {
    notYetPostedFailure(eventType, "msg_1")
  }

  private EventLog notYetPostedFailure(String eventType, String mandrillMessageId) {
    failureRow(eventType, mandrillMessageId, notYetPostedData(eventType, bounce), t0)
  }

  private EventLog notYetPostedFailure(String eventType, String reason, Instant at) {
    failureRow(eventType, "msg_1", notYetPostedData(eventType, reason), at)
  }

  private EventLog postedFailure(String eventType) {
    failureRow(eventType, "msg_1", postedData(eventType, bounce), t0)
  }

  private EventLog givenUpFailure(String eventType) {
    failureRow(eventType, "msg_1", givenUpData(eventType), t0)
  }

  private EventLog failureRow(String eventType, String mandrillMessageId, Map data, Instant at) {
    EventLog.builder()
        .type("TRADE_ORDER_EMAIL_" + eventType.toUpperCase())
        .principal(mandrillMessageId)
        .timestamp(at)
        .data(data)
        .build()
  }
}
