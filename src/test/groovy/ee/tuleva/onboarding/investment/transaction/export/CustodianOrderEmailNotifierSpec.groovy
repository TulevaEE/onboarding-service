package ee.tuleva.onboarding.investment.transaction.export

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.event.EventLog
import ee.tuleva.onboarding.event.EventLogRepository
import ee.tuleva.onboarding.notification.OperationsNotificationService
import ee.tuleva.onboarding.notification.email.EmailSendResult
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.client.RestClientException
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.FAILED
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.SENT
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.UNKNOWN
import static java.time.ZoneOffset.UTC
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW

class CustodianOrderEmailNotifierSpec extends Specification {

  OperationsNotificationService notificationService = Mock()
  EventLogRepository eventLogRepository = Mock()
  PlatformTransactionManager transactionManager = Mock()
  Instant sentAt = Instant.parse("2026-01-15T10:00:00Z")
  CustodianOrderEmailNotifier notifier = new CustodianOrderEmailNotifier(
      notificationService, new OrderEmailAlertRows(eventLogRepository, Clock.fixed(sentAt, UTC), transactionManager))

  String greenLine = "✅ Orders emailed: TUV100 ostu-/müügikorraldused 15.01.2026, from orders@example.com, 2 files"
  String resultUnknown =
      "🔴 Orders email result UNKNOWN: TUV100 ostu-/müügikorraldused 15.01.2026\n" +
          "No send result was recorded: the service may have stopped while sending.\n" +
          "It may have gone out. Check the Cc copy or ask the desk before sending again."

  Closure<Optional<EventLog>> latestSendResultRow = { Optional.empty() }

  def setup() {
    eventLogRepository.findFirstByTypeAndPrincipalOrderByTimestampDesc("TRADE_ORDER_EMAIL_SEND_RESULT", "42") >> {
      latestSendResultRow()
    }
  }

  MandrillMessage message = new MandrillMessage().tap {
    subject = "TUV100 ostu-/müügikorraldused 15.01.2026"
    fromEmail = "orders@example.com"
    attachments = [attachment("orders.xlsx"), attachment("orders.csv")]
  }

  def "posts a green line for the trading channel when the email was accepted for every recipient"() {
    given:
    def result = new EmailSendResult(SENT, [
        status("desk@example.com", "sent", null),
        status("copy@example.com", "queued", null)], null)

    when:
    notifier.reportSendResult(42L, message, result)

    then:
    1 * notificationService.sendMessage(
        "✅ Orders emailed: TUV100 ostu-/müügikorraldused 15.01.2026, from orders@example.com, 2 files",
        TRADING, INFO)
  }

  def "posts a red line saying not to send again when the orders went out but a copy was not accepted"() {
    given:
    def result = new EmailSendResult(SENT, [
        status("desk@example.com", "sent", null),
        status("copy@example.com", "rejected", "hard-bounce")], null)

    when:
    notifier.reportSendResult(42L, message, result)

    then:
    1 * notificationService.sendMessage(
        "🔴 Orders emailed, but not to every copy: TUV100 ostu-/müügikorraldused 15.01.2026, from orders@example.com, 2 files\n" +
            "Not accepted: copy@example.com rejected (hard-bounce)\n" +
            "The orders went out, don't send them again. Find out why Mandrill rejected the copy.",
        TRADING, ERROR)
  }

  def "posts a red line saying nothing went out when Mandrill rejected the email"() {
    given:
    def result = new EmailSendResult(FAILED, [
        status("desk@example.com", "rejected", "hard-bounce"),
        status("copy@example.com", "sent", null)],
        "Mandrill did not accept email: status=rejected, rejectReason=hard-bounce, id=id-desk")

    when:
    notifier.reportSendResult(42L, message, result)

    then:
    1 * notificationService.sendMessage(
        "🔴 Orders NOT sent: TUV100 ostu-/müügikorraldused 15.01.2026\n" +
            "Not accepted: desk@example.com rejected (hard-bounce)\n" +
            "Nothing went out to the desk. Fix the cause, then send the orders again.",
        TRADING, ERROR)
  }

  def "posts the problem when the email failed before Mandrill answered"() {
    given:
    def result = new EmailSendResult(FAILED, [], "ConnectException: Connection refused")

    when:
    notifier.reportSendResult(42L, message, result)

    then:
    1 * notificationService.sendMessage(
        "🔴 Orders NOT sent: TUV100 ostu-/müügikorraldused 15.01.2026\n" +
            "ConnectException: Connection refused\n" +
            "Nothing went out to the desk. Fix the cause, then send the orders again.",
        TRADING, ERROR)
  }

  def "posts a red line asking to check the copy before sending again when the result is unknown"() {
    given:
    def result = new EmailSendResult(UNKNOWN, [], "SocketTimeoutException: Read timed out")

    when:
    notifier.reportSendResult(42L, message, result)

    then:
    1 * notificationService.sendMessage(
        "🔴 Orders email result UNKNOWN: TUV100 ostu-/müügikorraldused 15.01.2026\n" +
            "SocketTimeoutException: Read timed out\n" +
            "It may have gone out. Check the Cc copy or ask the desk before sending again.",
        TRADING, ERROR)
  }

  def "lists the recipients Mandrill did not accept when only some main recipients got the email"() {
    given:
    def result = new EmailSendResult(UNKNOWN, [
        status("desk@example.com", "sent", null),
        status("desk-backup@example.com", "invalid", null)],
        "Mandrill did not accept email: status=invalid, rejectReason=null, id=id-backup")

    when:
    notifier.reportSendResult(42L, message, result)

    then:
    1 * notificationService.sendMessage(
        "🔴 Orders email result UNKNOWN: TUV100 ostu-/müügikorraldused 15.01.2026\n" +
            "Not accepted: desk-backup@example.com invalid\n" +
            "It may have gone out. Check the Cc copy or ask the desk before sending again.",
        TRADING, ERROR)
  }

  def "records the send attempt before the batch commits, holding the UNKNOWN line the run posts if no result follows"() {
    when:
    notifier.reportSendAttempt(42L, message)

    then:
    1 * eventLogRepository.save({ EventLog it ->
      it.type == "TRADE_ORDER_EMAIL_SEND_RESULT" &&
          it.principal == "42" &&
          it.timestamp == sentAt &&
          it.data == [text: resultUnknown, severity: "ERROR", alert: "SENDING"]
    })
    0 * notificationService._
  }

  def "records the send attempt in the batch's own transaction, so it commits with the batch"() {
    when:
    notifier.reportSendAttempt(42L, message)

    then:
    0 * transactionManager.getTransaction(_)
  }

  def "a send attempt that cannot be recorded fails the batch finalization, so the batch is not SENT without it"() {
    given:
    eventLogRepository.save(_) >> { throw new DataAccessResourceFailureException("database is down") }

    when:
    notifier.reportSendAttempt(42L, message)

    then:
    thrown(DataAccessResourceFailureException)
  }

  def "turns the attempt row into the result, posts it at once, then marks it posted, so the run has nothing left"() {
    given:
    def attempt = EventLog.builder()
        .id(7L)
        .type("TRADE_ORDER_EMAIL_SEND_RESULT")
        .principal("42")
        .timestamp(sentAt.minusSeconds(30))
        .data([text: resultUnknown, severity: "ERROR", alert: "SENDING"])
        .build()
    latestSendResultRow = { Optional.of(attempt) }

    when:
    notifier.reportSendResult(42L, message, new EmailSendResult(SENT, [], null))

    then:
    1 * eventLogRepository.save({ EventLog it ->
      it.id == 7L && it.timestamp == sentAt && it.data == [text: greenLine, severity: "INFO", alert: "NOT_POSTED"]
    }) >> { EventLog it -> it }

    then:
    1 * notificationService.sendMessage(greenLine, TRADING, INFO)

    then:
    1 * eventLogRepository.save({ EventLog it -> it.id == 7L && it.data == [text: greenLine, severity: "INFO", alert: "POSTED"] })
  }

  def "a result whose first write fails is written once more after the post, so the attempt row does not stay SENDING"() {
    given:
    latestSendResultRow = {
      Optional.of(EventLog.builder()
          .id(7L)
          .type("TRADE_ORDER_EMAIL_SEND_RESULT")
          .principal("42")
          .timestamp(sentAt.minusSeconds(30))
          .data([text: resultUnknown, severity: "ERROR", alert: "SENDING"])
          .build())
    }
    eventLogRepository.save(_) >> { throw new DataAccessResourceFailureException("database blip") } >> { EventLog it -> it }

    when:
    notifier.reportSendResult(42L, message, new EmailSendResult(SENT, [], null))

    then:
    1 * notificationService.sendMessage(greenLine, TRADING, INFO)

    then:
    1 * eventLogRepository.save({ EventLog it -> it.id == 7L && it.data == [text: greenLine, severity: "INFO", alert: "POSTED"] })
  }

  def "a latest row with no data is in no state, so the result is written as a row of its own and still posted"() {
    given:
    latestSendResultRow = {
      Optional.of(EventLog.builder().id(9L).type("TRADE_ORDER_EMAIL_SEND_RESULT").principal("42").timestamp(sentAt).build())
    }

    when:
    notifier.reportSendResult(42L, message, new EmailSendResult(SENT, [], null))

    then:
    noExceptionThrown()
    1 * eventLogRepository.save({ EventLog it ->
      it.id == null && it.data == [text: greenLine, severity: "INFO", alert: "NOT_POSTED"]
    })
    1 * notificationService.sendMessage(greenLine, TRADING, INFO)
  }

  def "posts a red line saying nothing went out when the order email could not be built"() {
    when:
    notifier.reportNotBuilt(
        42L, "TUV100 ostu-/müügikorraldused 15.01.2026", "java.lang.IllegalStateException: an export has no file name")

    then:
    1 * eventLogRepository.save({ EventLog it -> it.principal == "42" && it.data.alert == "NOT_POSTED" })
    1 * notificationService.sendMessage(
        "🔴 Orders NOT sent: TUV100 ostu-/müügikorraldused 15.01.2026\n" +
            "The message could not be built: java.lang.IllegalStateException: an export has no file name\n" +
            "Nothing went out to the desk. Fix the cause, then send the orders again.",
        TRADING, ERROR)
  }

  def "records the result as a row of its own when the attempt could not be recorded"() {
    when:
    notifier.reportSendResult(42L, message, new EmailSendResult(SENT, [], null))

    then:
    1 * eventLogRepository.save({ EventLog it ->
      it.type == "TRADE_ORDER_EMAIL_SEND_RESULT" &&
          it.principal == "42" &&
          it.timestamp == sentAt &&
          it.data == [text: greenLine, severity: "INFO", alert: "NOT_POSTED"]
    }) >> { EventLog it -> it }
  }

  def "records and marks the result in transactions of its own, since the batch has already committed"() {
    given:
    eventLogRepository.save(_) >> { EventLog it -> it }

    when:
    notifier.reportSendResult(42L, message, new EmailSendResult(SENT, [], null))

    then:
    2 * transactionManager.getTransaction({ it.propagationBehavior == PROPAGATION_REQUIRES_NEW })
  }

  def "a result Slack does not take at once stays recorded as not posted, for the run"() {
    given:
    notificationService.sendMessage(_, _, _) >> { throw new RestClientException("Slack is down") }

    when:
    notifier.reportSendResult(42L, message, new EmailSendResult(SENT, [], null))

    then:
    noExceptionThrown()
    1 * eventLogRepository.save({ EventLog it -> it.data.alert == "NOT_POSTED" }) >> { EventLog it -> it }
    0 * eventLogRepository.save({ EventLog it -> it.data.alert == "POSTED" })
  }

  def "a result that cannot be recorded is still posted at once"() {
    given:
    eventLogRepository.save(_) >> { throw new DataAccessResourceFailureException("database is down") }

    when:
    notifier.reportSendResult(42L, message, new EmailSendResult(SENT, [], null))

    then:
    noExceptionThrown()
    1 * notificationService.sendMessage(greenLine, TRADING, INFO)
  }

  private static MessageContent attachment(String name) {
    new MessageContent().tap { it.name = name }
  }

  private static MandrillMessageStatus status(String email, String status, String rejectReason) {
    new MandrillMessageStatus().tap {
      it.email = email
      it.status = status
      it.reject_reason = rejectReason
      it._id = "id-" + email
    }
  }
}
