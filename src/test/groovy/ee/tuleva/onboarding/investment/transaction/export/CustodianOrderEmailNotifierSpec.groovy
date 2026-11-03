package ee.tuleva.onboarding.investment.transaction.export

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.notification.OperationsNotificationService
import ee.tuleva.onboarding.notification.email.EmailSendResult
import org.springframework.web.client.RestClientException
import spock.lang.Specification

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.FAILED
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.SENT
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.UNKNOWN

class CustodianOrderEmailNotifierSpec extends Specification {

  OperationsNotificationService notificationService = Mock()
  CustodianOrderEmailNotifier notifier = new CustodianOrderEmailNotifier(notificationService)

  MandrillMessage message = new MandrillMessage().tap {
    subject = "TUV100 ostu-/müügikorraldused 15.01.2026"
    fromEmail = "orders@example.com"
    attachments = [attachment("orders.xlsx"), attachment("orders.csv")]
  }

  def "posts a green line to the trading channel when the email was accepted for every recipient"() {
    given:
    def result = new EmailSendResult(SENT, [
        status("desk@example.com", "sent", null),
        status("copy@example.com", "queued", null)], null)

    when:
    notifier.reportSendResult(message, result)

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
    notifier.reportSendResult(message, result)

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
    notifier.reportSendResult(message, result)

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
    notifier.reportSendResult(message, result)

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
    notifier.reportSendResult(message, result)

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
    notifier.reportSendResult(message, result)

    then:
    1 * notificationService.sendMessage(
        "🔴 Orders email result UNKNOWN: TUV100 ostu-/müügikorraldused 15.01.2026\n" +
            "Not accepted: desk-backup@example.com invalid\n" +
            "It may have gone out. Check the Cc copy or ask the desk before sending again.",
        TRADING, ERROR)
  }

  def "keeps going when Slack cannot be reached"() {
    given:
    notificationService.sendMessage(_, _, _) >> { throw new RestClientException("Slack is down") }

    when:
    notifier.reportSendResult(message, new EmailSendResult(SENT, [], null))

    then:
    noExceptionThrown()
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
