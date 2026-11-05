package ee.tuleva.onboarding.investment.transaction.export

import ee.tuleva.onboarding.event.EventLog
import ee.tuleva.onboarding.event.EventLogRepository
import ee.tuleva.onboarding.notification.OperationsNotificationService
import ee.tuleva.onboarding.notification.email.EmailDeliveryFailedEvent
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING
import static java.time.ZoneOffset.UTC
import static org.springframework.http.HttpStatus.BAD_GATEWAY
import static org.springframework.http.HttpStatus.BAD_REQUEST
import static org.springframework.http.HttpStatus.FORBIDDEN
import static org.springframework.http.HttpStatus.NOT_FOUND
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW

class CustodianOrderEmailAlerterSpec extends Specification {

  OperationsNotificationService notificationService = Mock()
  EventLogRepository eventLogRepository = Mock()
  PlatformTransactionManager transactionManager = Mock()

  Instant t0 = Instant.parse("2026-01-15T10:01:00Z")
  Instant aMinuteLater = t0.plus(Duration.ofMinutes(1))
  String subject = "TUV100 ostu-/müügikorraldused 15.01.2026"
  String bounce = "550 mailbox unavailable"
  String greenLine = "✅ Orders emailed: $subject, 2 files"
  String redAlert = redAlert("hard_bounce", "msg_1")

  CustodianOrderEmailAlerter alerter = alerterAt(aMinuteLater)

  def "records an undelivered order email with its red alert for the next run to post, without calling Slack: #eventType"() {
    given:
    eventLogRepository.existsByTypeAndPrincipal(recordedType, "msg_1") >> false

    when:
    alerter.onEmailDeliveryFailed(orderEmailFailure(eventType, bounce))

    then:
    1 * eventLogRepository.save({ EventLog it ->
      it.type == recordedType &&
          it.principal == "msg_1" &&
          it.timestamp == t0 &&
          it.data == notYetPostedData(redAlert(eventType, "msg_1"), "ERROR")
    })
    0 * notificationService._

    where:
    eventType     | recordedType
    "hard_bounce" | "TRADE_ORDER_EMAIL_HARD_BOUNCE"
    "soft_bounce" | "TRADE_ORDER_EMAIL_SOFT_BOUNCE"
    "reject"      | "TRADE_ORDER_EMAIL_REJECT"
  }

  def "records a delayed order email with its amber alert"() {
    given:
    eventLogRepository.existsByTypeAndPrincipal("TRADE_ORDER_EMAIL_DEFERRAL", "msg_1") >> false

    when:
    alerter.onEmailDeliveryFailed(orderEmailFailure("deferral", "450 4.7.25 try again later"))

    then:
    1 * eventLogRepository.save({ EventLog it ->
      it.type == "TRADE_ORDER_EMAIL_DEFERRAL" &&
          it.data == notYetPostedData(
          "🟠 Order email delayed: to=desk@example.com, subject=$subject\n" +
              "deferral: 450 4.7.25 try again later (id=msg_1)\n" +
              "Mandrill keeps trying. If the desk hasn't confirmed the orders before the cut-off, check with the desk.",
          "WARNING")
    })
    0 * notificationService._
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
    context.registerBean(CustodianOrderEmailAlerter, { -> alerter } as java.util.function.Supplier)
    context.refresh()

    when:
    context.publishEvent(orderEmailFailure("hard_bounce", bounce))

    then:
    thrown(IllegalStateException)
    1 * eventLogRepository.save({ EventLog it -> it.type == "TRADE_ORDER_EMAIL_HARD_BOUNCE" })

    cleanup:
    context.close()
  }

  def "posts a send result at once: records it, posts it, then marks it posted, so the run has nothing left"() {
    when:
    alerter.postSendResult("42", greenLine, INFO)

    then:
    1 * eventLogRepository.save({ EventLog it ->
      it.type == "TRADE_ORDER_EMAIL_SEND_RESULT" &&
          it.principal == "42" &&
          it.timestamp == aMinuteLater &&
          it.data == notYetPostedData(greenLine, "INFO")
    })

    then:
    1 * notificationService.sendMessage(greenLine, TRADING, INFO)

    then:
    1 * eventLogRepository.save({ EventLog it -> it.principal == "42" && it.data == postedData(greenLine, "INFO") })
  }

  def "records and marks a send result in transactions of its own, since the batch has already committed"() {
    when:
    alerter.postSendResult("42", greenLine, INFO)

    then:
    2 * transactionManager.getTransaction({ it.propagationBehavior == PROPAGATION_REQUIRES_NEW })
  }

  def "a send result Slack does not take at once stays recorded as not posted, for the run"() {
    given:
    notificationService.sendMessage(_, _, _) >> { throw new RestClientException("Slack is down") }

    when:
    alerter.postSendResult("42", redAlert, ERROR)

    then:
    noExceptionThrown()
    1 * eventLogRepository.save({ EventLog it -> it.data == notYetPostedData(redAlert, "ERROR") })
    0 * eventLogRepository.save({ EventLog it -> it.data.alert == "POSTED" })
  }

  def "a send result that cannot be recorded is still posted at once"() {
    given:
    eventLogRepository.save(_) >> { throw new DataAccessResourceFailureException("database is down") }

    when:
    alerter.postSendResult("42", redAlert, ERROR)

    then:
    noExceptionThrown()
    1 * notificationService.sendMessage(redAlert, TRADING, ERROR)
  }

  def "the run leaves a send result to its direct post for two minutes, longer than the Slack client's timeouts: #age"() {
    given:
    alerter = alerterAt(t0.plus(age))
    rowsRead(sendResultRow(t0))

    when:
    alerter.postAlertsNotYetPosted()

    then:
    posts * notificationService.sendMessage(greenLine, TRADING, INFO)

    where:
    age                                  | posts
    Duration.ofMinutes(2).minusSeconds(1) | 0
    Duration.ofMinutes(2).plusSeconds(1)  | 1
  }

  def "the run reads order email delivery failures and send results of the last two days, oldest first"() {
    when:
    alerter.postAlertsNotYetPosted()

    then:
    1 * eventLogRepository.findAllByTypeInAndTimestampGreaterThanEqualOrderByTimestamp(
        ["TRADE_ORDER_EMAIL_HARD_BOUNCE", "TRADE_ORDER_EMAIL_SOFT_BOUNCE",
         "TRADE_ORDER_EMAIL_REJECT", "TRADE_ORDER_EMAIL_DEFERRAL",
         "TRADE_ORDER_EMAIL_SEND_RESULT"],
        aMinuteLater.minus(Duration.ofHours(48))) >> []
  }

  def "the run posts a recorded alert with its severity and then marks it posted: #severity"() {
    given:
    rowsRead(notYetPostedRow("msg_1", text, severity.name(), t0))

    when:
    alerter.postAlertsNotYetPosted()

    then:
    1 * notificationService.sendMessage(text, TRADING, severity)

    then:
    1 * eventLogRepository.save({ EventLog it ->
      it.principal == "msg_1" && it.data == postedData(text, severity.name())
    })

    where:
    severity | text
    INFO     | "✅ Orders emailed: TUV100 ostu-/müügikorraldused 15.01.2026, 2 files"
    WARNING  | "🟠 Order email delayed: to=desk@example.com\ndeferral: 450 (id=msg_1)\nMandrill keeps trying."
    ERROR    | "🔴 Order email NOT delivered: to=desk@example.com\nhard_bounce: 550 (id=msg_1)\nCheck with the desk."
  }

  def "a post that goes out more than five minutes after the event says on its first line when the event happened"() {
    given:
    alerter = alerterAt(t0.plus(Duration.ofHours(3)))
    rowsRead(notYetPostedRow("msg_1", text, "ERROR", t0))

    when:
    alerter.postAlertsNotYetPosted()

    then:
    1 * notificationService.sendMessage(posted, TRADING, ERROR)

    where:
    text                                  | posted
    "🔴 Orders NOT sent: SUBJ\ncause\nact" | "🔴 Orders NOT sent: SUBJ (at 15.01.2026 12:01)\ncause\nact"
    "✅ Orders emailed: SUBJ, 2 files"     | "✅ Orders emailed: SUBJ, 2 files (at 15.01.2026 12:01)"
  }

  def "the run does not post a row that was already posted or given up"() {
    given:
    rowsRead(postedRow("msg_1"), givenUpRow("msg_2"))

    when:
    alerter.postAlertsNotYetPosted()

    then:
    0 * notificationService._
    0 * eventLogRepository.save(_)
  }

  def "a post Slack does not take stays not posted for the next run, and the others are still posted"() {
    given:
    rowsRead(notYetPostedRow("msg_1", redAlert, "ERROR", t0),
        notYetPostedRow("msg_2", redAlert("reject", "msg_2"), "ERROR", t0))
    notificationService.sendMessage({ it.contains("id=msg_1") }, _, _) >> { throw new RestClientException("Slack is down") }

    when:
    alerter.postAlertsNotYetPosted()

    then:
    noExceptionThrown()
    1 * eventLogRepository.save({ EventLog it -> it.principal == "msg_2" && it.data == postedData(redAlert("reject", "msg_2"), "ERROR") })
    0 * eventLogRepository.save({ EventLog it -> it.principal == "msg_1" })
  }

  def "a row whose data cannot be read is given up at once instead of retried: #problem"() {
    given:
    rowsRead(row("msg_1", unreadableData, t0))

    when:
    alerter.postAlertsNotYetPosted()

    then:
    noExceptionThrown()
    0 * notificationService._
    1 * eventLogRepository.save({ EventLog it -> it.data == unreadableData + [alert: "GIVEN_UP"] })

    where:
    problem                     | unreadableData
    "no text"                   | [severity: "ERROR", alert: "NOT_POSTED"]
    "no severity"               | [text: "🔴 Orders NOT sent: SUBJ", alert: "NOT_POSTED"]
    "not a known severity"      | [text: "🔴 Orders NOT sent: SUBJ", severity: "LOUD", alert: "NOT_POSTED"]
    "text that is not a string" | [text: 42, severity: "ERROR", alert: "NOT_POSTED"]
  }

  def "four rows that cannot be read do not keep a newer row from being posted in the same run"() {
    given:
    def unreadable = (1..4).collect { row("msg_$it", [text: 42, alert: "NOT_POSTED"], t0) }
    def newer = notYetPostedRow("msg_5", redAlert("reject", "msg_5"), "ERROR", t0.plusSeconds(30))
    rowsRead((unreadable + [newer]) as EventLog[])

    when:
    alerter.postAlertsNotYetPosted()

    then:
    1 * notificationService.sendMessage(redAlert("reject", "msg_5"), TRADING, ERROR)
    4 * eventLogRepository.save({ EventLog it -> it.data.alert == "GIVEN_UP" })
    1 * eventLogRepository.save({ EventLog it -> it.principal == "msg_5" && it.data == postedData(redAlert("reject", "msg_5"), "ERROR") })
  }

  def "a row whose payload Slack refuses is given up at once instead of retried: #status"() {
    given:
    rowsRead(notYetPostedRow("msg_1", redAlert, "ERROR", t0))
    notificationService.sendMessage(_, _, _) >> { throw new HttpClientErrorException(status) }

    when:
    alerter.postAlertsNotYetPosted()

    then:
    noExceptionThrown()
    1 * eventLogRepository.save({ EventLog it -> it.data == givenUpData(redAlert, "ERROR") })

    where:
    status << [BAD_REQUEST, CONTENT_TOO_LARGE]
  }

  def "a row Slack cannot take for now stays not posted for the next run, since the same hits every row: #problem"() {
    given:
    rowsRead(notYetPostedRow("msg_1", redAlert, "ERROR", t0))
    notificationService.sendMessage(_, _, _) >> { throw exception }

    when:
    alerter.postAlertsNotYetPosted()

    then:
    noExceptionThrown()
    0 * eventLogRepository.save(_)

    where:
    problem                     | exception
    "rate limited"              | new HttpClientErrorException(TOO_MANY_REQUESTS)
    "webhook no longer allowed" | new HttpClientErrorException(FORBIDDEN)
    "channel not found"         | new HttpClientErrorException(NOT_FOUND)
    "Slack outage"              | new HttpServerErrorException(BAD_GATEWAY)
    "timeout"                   | new ResourceAccessException("Read timed out")
  }

  def "four rows whose payload Slack refuses do not keep a newer red row from being posted in the same run"() {
    given:
    def refused = (1..4).collect { notYetPostedRow("msg_$it", redAlert("hard_bounce", "msg_$it"), "ERROR", t0) }
    def newer = notYetPostedRow("msg_5", redAlert("reject", "msg_5"), "ERROR", t0.plusSeconds(30))
    rowsRead((refused + [newer]) as EventLog[])
    notificationService.sendMessage({ !it.contains("id=msg_5") }, _, _) >> { throw new HttpClientErrorException(BAD_REQUEST) }

    when:
    alerter.postAlertsNotYetPosted()

    then:
    1 * notificationService.sendMessage(redAlert("reject", "msg_5"), TRADING, ERROR)
    4 * eventLogRepository.save({ EventLog it -> it.data.alert == "GIVEN_UP" })
    1 * eventLogRepository.save({ EventLog it -> it.principal == "msg_5" && it.data.alert == "POSTED" })
  }

  def "a run posts red rows first, then amber, then green, each oldest first"() {
    given:
    rowsRead(
        notYetPostedRow("green_1", "✅ green 1", "INFO", t0),
        notYetPostedRow("amber_1", "🟠 amber 1", "WARNING", t0.plusSeconds(1)),
        notYetPostedRow("green_2", "✅ green 2", "INFO", t0.plusSeconds(2)),
        notYetPostedRow("red_1", "🔴 red 1", "ERROR", t0.plusSeconds(3)),
        notYetPostedRow("red_2", "🔴 red 2", "ERROR", t0.plusSeconds(4)))

    when:
    alerter.postAlertsNotYetPosted()

    then:
    1 * notificationService.sendMessage("🔴 red 1", TRADING, ERROR)

    then:
    1 * notificationService.sendMessage("🔴 red 2", TRADING, ERROR)

    then:
    1 * notificationService.sendMessage("🟠 amber 1", TRADING, WARNING)

    then:
    1 * notificationService.sendMessage("✅ green 1", TRADING, INFO)
    0 * notificationService.sendMessage("✅ green 2", _, _)
  }

  def "a row still not posted at the end of the 24 hour retry window is given up instead of posted"() {
    given:
    alerter = alerterAt(t0.plus(Duration.ofHours(24)).plusSeconds(1))
    rowsRead(notYetPostedRow("msg_1", redAlert, "ERROR", t0))

    when:
    alerter.postAlertsNotYetPosted()

    then:
    0 * notificationService._
    1 * eventLogRepository.save({ EventLog it -> it.data == givenUpData(redAlert, "ERROR") })
  }

  def "a run posts at most four rows, so four hanging Slack posts still end inside the 5 minute lock"() {
    given:
    rowsRead((1..5).collect { notYetPostedRow("msg_$it", redAlert("hard_bounce", "msg_$it"), "ERROR", t0) } as EventLog[])

    when:
    alerter.postAlertsNotYetPosted()

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

  private CustodianOrderEmailAlerter alerterAt(Instant now) {
    new CustodianOrderEmailAlerter(
        notificationService, eventLogRepository, Clock.fixed(now, UTC), transactionManager)
  }

  private void rowsRead(EventLog... rows) {
    eventLogRepository.findAllByTypeInAndTimestampGreaterThanEqualOrderByTimestamp(_, _) >> rows.toList()
  }

  private EmailDeliveryFailedEvent orderEmailFailure(String eventType, String reason) {
    new EmailDeliveryFailedEvent(
        "desk@example.com", subject, eventType, reason, "msg_1", t0, ["trade-order"])
  }

  private String redAlert(String eventType, String mandrillMessageId) {
    "🔴 Order email NOT delivered: to=desk@example.com, subject=$subject\n" +
        "$eventType: 550 mailbox unavailable (id=$mandrillMessageId)\n" +
        "The orders did not reach this address. Check with the desk before sending them again."
  }

  private static Map notYetPostedData(String text, String severity) {
    [text: text, severity: severity, alert: "NOT_POSTED"]
  }

  private static Map postedData(String text, String severity) {
    [text: text, severity: severity, alert: "POSTED"]
  }

  private static Map givenUpData(String text, String severity) {
    [text: text, severity: severity, alert: "GIVEN_UP"]
  }

  private EventLog notYetPostedRow(String principal, String text, String severity, Instant at) {
    row(principal, notYetPostedData(text, severity), at)
  }

  private EventLog postedRow(String principal) {
    row(principal, postedData(greenLine, "INFO"), t0)
  }

  private EventLog givenUpRow(String principal) {
    row(principal, givenUpData(redAlert, "ERROR"), t0)
  }

  private EventLog sendResultRow(Instant at) {
    EventLog.builder()
        .type("TRADE_ORDER_EMAIL_SEND_RESULT")
        .principal("42")
        .timestamp(at)
        .data(notYetPostedData(greenLine, "INFO"))
        .build()
  }

  private static EventLog row(String principal, Map data, Instant at) {
    EventLog.builder()
        .type("TRADE_ORDER_EMAIL_HARD_BOUNCE")
        .principal(principal)
        .timestamp(at)
        .data(data)
        .build()
  }
}
