package ee.tuleva.onboarding.investment.transaction.export

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.event.EventLog
import ee.tuleva.onboarding.event.EventLogRepository
import ee.tuleva.onboarding.notification.email.EmailSendResult
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.FAILED
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.SENT
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.UNKNOWN
import static java.time.ZoneOffset.UTC
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW

class CustodianOrderEmailNotifierSpec extends Specification {

  EventLogRepository eventLogRepository = Mock()
  PlatformTransactionManager transactionManager = Mock()
  Instant sentAt = Instant.parse("2026-01-15T10:00:00Z")
  CustodianOrderEmailNotifier notifier =
      new CustodianOrderEmailNotifier(eventLogRepository, transactionManager, Clock.fixed(sentAt, UTC))

  MandrillMessage message = new MandrillMessage().tap {
    subject = "TUV100 ostu-/müügikorraldused 15.01.2026"
    fromEmail = "orders@example.com"
    attachments = [attachment("orders.xlsx"), attachment("orders.csv")]
  }

  def "records a green line for the trading channel when the email was accepted for every recipient"() {
    given:
    def result = new EmailSendResult(SENT, [
        status("desk@example.com", "sent", null),
        status("copy@example.com", "queued", null)], null)

    when:
    notifier.reportSendResult(42L, message, result)

    then:
    1 * eventLogRepository.save({ EventLog it -> isRecorded(it,
        "✅ Orders emailed: TUV100 ostu-/müügikorraldused 15.01.2026, from orders@example.com, 2 files",
        "INFO") })
  }

  def "records a red line saying not to send again when the orders went out but a copy was not accepted"() {
    given:
    def result = new EmailSendResult(SENT, [
        status("desk@example.com", "sent", null),
        status("copy@example.com", "rejected", "hard-bounce")], null)

    when:
    notifier.reportSendResult(42L, message, result)

    then:
    1 * eventLogRepository.save({ EventLog it -> isRecorded(it,
        "🔴 Orders emailed, but not to every copy: TUV100 ostu-/müügikorraldused 15.01.2026, from orders@example.com, 2 files\n" +
            "Not accepted: copy@example.com rejected (hard-bounce)\n" +
            "The orders went out, don't send them again. Find out why Mandrill rejected the copy.",
        "ERROR") })
  }

  def "records a red line saying nothing went out when Mandrill rejected the email"() {
    given:
    def result = new EmailSendResult(FAILED, [
        status("desk@example.com", "rejected", "hard-bounce"),
        status("copy@example.com", "sent", null)],
        "Mandrill did not accept email: status=rejected, rejectReason=hard-bounce, id=id-desk")

    when:
    notifier.reportSendResult(42L, message, result)

    then:
    1 * eventLogRepository.save({ EventLog it -> isRecorded(it,
        "🔴 Orders NOT sent: TUV100 ostu-/müügikorraldused 15.01.2026\n" +
            "Not accepted: desk@example.com rejected (hard-bounce)\n" +
            "Nothing went out to the desk. Fix the cause, then send the orders again.",
        "ERROR") })
  }

  def "records the problem when the email failed before Mandrill answered"() {
    given:
    def result = new EmailSendResult(FAILED, [], "ConnectException: Connection refused")

    when:
    notifier.reportSendResult(42L, message, result)

    then:
    1 * eventLogRepository.save({ EventLog it -> isRecorded(it,
        "🔴 Orders NOT sent: TUV100 ostu-/müügikorraldused 15.01.2026\n" +
            "ConnectException: Connection refused\n" +
            "Nothing went out to the desk. Fix the cause, then send the orders again.",
        "ERROR") })
  }

  def "records a red line asking to check the copy before sending again when the result is unknown"() {
    given:
    def result = new EmailSendResult(UNKNOWN, [], "SocketTimeoutException: Read timed out")

    when:
    notifier.reportSendResult(42L, message, result)

    then:
    1 * eventLogRepository.save({ EventLog it -> isRecorded(it,
        "🔴 Orders email result UNKNOWN: TUV100 ostu-/müügikorraldused 15.01.2026\n" +
            "SocketTimeoutException: Read timed out\n" +
            "It may have gone out. Check the Cc copy or ask the desk before sending again.",
        "ERROR") })
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
    1 * eventLogRepository.save({ EventLog it -> isRecorded(it,
        "🔴 Orders email result UNKNOWN: TUV100 ostu-/müügikorraldused 15.01.2026\n" +
            "Not accepted: desk-backup@example.com invalid\n" +
            "It may have gone out. Check the Cc copy or ask the desk before sending again.",
        "ERROR") })
  }

  def "records the result in a transaction of its own, since the batch has already committed"() {
    when:
    notifier.reportSendResult(42L, message, new EmailSendResult(SENT, [], null))

    then:
    1 * transactionManager.getTransaction({ it.propagationBehavior == PROPAGATION_REQUIRES_NEW })

    then:
    1 * eventLogRepository.save(_)

    then:
    1 * transactionManager.commit(_)
  }

  def "keeps going when the result cannot be recorded"() {
    given:
    eventLogRepository.save(_) >> { throw new DataAccessResourceFailureException("database is down") }

    when:
    notifier.reportSendResult(42L, message, new EmailSendResult(SENT, [], null))

    then:
    noExceptionThrown()
  }

  private boolean isRecorded(EventLog row, String text, String severity) {
    row.type == "TRADE_ORDER_EMAIL_SEND_RESULT" &&
        row.principal == "42" &&
        row.timestamp == sentAt &&
        row.data == [text: text, severity: severity, alert: "NOT_POSTED"]
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
