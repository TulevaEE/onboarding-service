package ee.tuleva.onboarding.notification.email

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import com.microtripit.mandrillapp.lutung.view.MandrillScheduledMessageInfo
import ee.tuleva.onboarding.user.User
import org.springframework.core.retry.RetryTemplate
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import com.google.gson.JsonSyntaxException
import spock.lang.Specification

import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.time.Instant

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.BCC
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class EmailServiceSpec extends Specification {

  MandrillClient mandrillClient = Mock()
  RetryTemplate retryTemplate = MandrillConfiguration.emailServiceRetryTemplateWithFirstDelay(Duration.ofMillis(1))
  EmailService service = new EmailService(mandrillClient, retryTemplate)
  User user = sampleUser().build()
  String templateName = "sample_template"
  MandrillMessage message = service.newMandrillMessage(
      user.email,
      templateName,
      [fname: user.firstName, lname: user.lastName],
      ["test"],
      null)
  MandrillMessageStatus mandrillMessageStatus = new MandrillMessageStatus().tap {
    _id = "123"
    status = "sent"
  }

  def "Can send email with template"() {
    when:
    def response = service.send(user, message, templateName)

    then:
    1 * mandrillClient.sendTemplate(templateName, message, null) >> [mandrillMessageStatus]
    response == Optional.of(mandrillMessageStatus)
  }

  def "Can send delayed emails with template"() {
    given:
    Instant sendAt = Instant.now()

    when:
    def response = service.send(user, message, templateName, sendAt)

    then:
    1 * mandrillClient.sendTemplate(templateName, message, sendAt) >> [mandrillMessageStatus]
    response == Optional.of(mandrillMessageStatus)
  }

  def "send returns empty when Mandrill returns rejected"() {
    given:
    def rejectedStatus = new MandrillMessageStatus()
    rejectedStatus.status = "rejected"

    when:
    def response = service.send(user, message, templateName)

    then:
    1 * mandrillClient.sendTemplate(templateName, message, null) >> [rejectedStatus]
    response == Optional.empty()
  }

  def "send returns empty when Mandrill returns invalid"() {
    given:
    def invalidStatus = new MandrillMessageStatus()
    invalidStatus.status = "invalid"

    when:
    def response = service.send(user, message, templateName)

    then:
    1 * mandrillClient.sendTemplate(templateName, message, null) >> [invalidStatus]
    response == Optional.empty()
  }

  def "attemptSend returns the answer when Mandrill refuses the recipient for good: #status #rejectReason"() {
    given:
    def refusal = new MandrillMessageStatus()
    refusal.status = status
    refusal.reject_reason = rejectReason

    when:
    def response = service.attemptSend(user, message, templateName)

    then:
    1 * mandrillClient.sendTemplate(templateName, message, null) >> [refusal]
    response == Optional.of(refusal)

    where:
    status     | rejectReason
    "rejected" | "hard-bounce"
    "rejected" | "spam"
    "rejected" | "unsub"
    "rejected" | "custom"
    "rejected" | "invalid"
    "invalid"  | null
  }

  def "attemptSend returns empty when Mandrill's refusal can clear before the next attempt: #rejectReason"() {
    given:
    def refusal = new MandrillMessageStatus()
    refusal.status = "rejected"
    refusal.reject_reason = rejectReason

    when:
    def response = service.attemptSend(user, message, templateName)

    then:
    1 * mandrillClient.sendTemplate(templateName, message, null) >> [refusal]
    response == Optional.empty()

    where:
    rejectReason << ["soft-bounce", "unsigned", "invalid-sender", "test-mode-limit", "rule", null]
  }

  def "attemptSend returns empty when Mandrill never answers"() {
    when:
    def response = service.attemptSend(user, message, templateName)

    then:
    1 * mandrillClient.sendTemplate(templateName, message, null) >> { throw new ResourceAccessException("connection reset") }
    response == Optional.empty()
  }

  def "sendFromBackgroundJob sends the template through the client for background jobs"() {
    when:
    def response = service.sendFromBackgroundJob(user, message, templateName)

    then:
    1 * mandrillClient.sendTemplateFromBackgroundJob(templateName, message) >> [mandrillMessageStatus]
    0 * mandrillClient.sendTemplate(*_)
    response == Optional.of(mandrillMessageStatus)
  }

  def "send sends the template through the client for a waiting user"() {
    when:
    def response = service.send(user, message, templateName)

    then:
    1 * mandrillClient.sendTemplate(templateName, message, null) >> [mandrillMessageStatus]
    0 * mandrillClient.sendTemplateFromBackgroundJob(*_)
    response == Optional.of(mandrillMessageStatus)
  }

  def "sendFromBackgroundJob returns empty when Mandrill refuses the recipient for good"() {
    given:
    def refusal = new MandrillMessageStatus().tap {
      status = "rejected"
      reject_reason = "hard-bounce"
    }

    when:
    def response = service.sendFromBackgroundJob(user, message, templateName)

    then:
    1 * mandrillClient.sendTemplateFromBackgroundJob(templateName, message) >> [refusal]
    response == Optional.empty()
  }

  def "attemptSendFromBackgroundJob returns the answer when Mandrill refuses the recipient for good"() {
    given:
    def refusal = new MandrillMessageStatus().tap {
      status = "rejected"
      reject_reason = "hard-bounce"
    }

    when:
    def response = service.attemptSendFromBackgroundJob(user, message, templateName)

    then:
    1 * mandrillClient.sendTemplateFromBackgroundJob(templateName, message) >> [refusal]
    0 * mandrillClient.sendTemplate(*_)
    response == Optional.of(refusal)
  }

  def "attemptSendFromBackgroundJob returns empty when Mandrill does not answer in time"() {
    when:
    def response = service.attemptSendFromBackgroundJob(user, message, templateName)

    then:
    1 * mandrillClient.sendTemplateFromBackgroundJob(templateName, message) >> {
      throw new ResourceAccessException("I/O error", new HttpTimeoutException("request timed out"))
    }
    response == Optional.empty()
  }

  def "attemptSendFromBackgroundJob does not send when Mandrill not initialised"() {
    given:
    def serviceWithoutMandrill = new EmailService(null, retryTemplate)

    when:
    def response = serviceWithoutMandrill.attemptSendFromBackgroundJob(user, message, templateName)

    then:
    response == Optional.empty()
  }

  def "Can cancel scheduled emails"() {
    given:
    String mandrillMessageId = "100"
    def info = new MandrillScheduledMessageInfo()
    1 * mandrillClient.cancelScheduled(mandrillMessageId) >> info

    when:
    def scheduledEmailInfo = service.cancelScheduledEmail(mandrillMessageId)

    then:
    scheduledEmailInfo.get() == info
  }

  def "attemptSend returns empty when Mandrill refuses the request"() {
    when:
    def response = service.attemptSend(user, message, templateName)

    then:
    1 * mandrillClient.sendTemplate(templateName, message, null) >> { throw mandrillError("Invalid_Key") }
    response == Optional.empty()
  }

  def "attemptSend returns empty when Mandrill not initialised"() {
    given:
    def serviceWithoutMandrill = new EmailService(null, retryTemplate)

    when:
    def response = serviceWithoutMandrill.attemptSend(user, message, templateName)

    then:
    response == Optional.empty()
  }

  def "cancelling a scheduled email returns empty when Mandrill #failure"() {
    given:
    String mandrillMessageId = "100"

    when:
    def scheduledEmailInfo = service.cancelScheduledEmail(mandrillMessageId)

    then:
    1 * mandrillClient.cancelScheduled(mandrillMessageId) >> { throw error }
    scheduledEmailInfo == Optional.empty()

    where:
    failure                                  | error
    "no longer knows the message"            | mandrillError("Unknown_Message")
    "refuses the request for another reason" | mandrillError("Invalid_Key")
    "cannot be reached"                      | new ResourceAccessException("Read timed out")
    "fails while its answer is being read"   | new RestClientException("Error while extracting response")
    "answers with a body that cannot be read" | new JsonSyntaxException("Expected BEGIN_OBJECT")
  }

  def "cancelling a scheduled email returns empty when Mandrill not initialised"() {
    given:
    def serviceWithoutMandrill = new EmailService(null, retryTemplate)

    when:
    def scheduledEmailInfo = serviceWithoutMandrill.cancelScheduledEmail("100")

    then:
    scheduledEmailInfo == Optional.empty()
  }

  def "Can send system email without template"() {
    given:
    def systemMessage = new MandrillMessage()

    when:
    def result = service.sendSystemEmail(systemMessage)

    then:
    1 * mandrillClient.send(systemMessage) >> [mandrillMessageStatus]
    result == true
  }

  def "sendSystemEmail returns false when Mandrill not initialised"() {
    given:
    def serviceWithoutMandrill = new EmailService(null, retryTemplate)
    def systemMessage = new MandrillMessage()

    when:
    def result = serviceWithoutMandrill.sendSystemEmail(systemMessage)

    then:
    result == false
  }

  def "sendSystemEmail makes #attempts attempt(s) and returns false when #failure"() {
    given:
    def systemMessage = new MandrillMessage()

    when:
    def result = service.sendSystemEmail(systemMessage)

    then:
    attempts * mandrillClient.send(systemMessage) >> { throw thrown }
    result == false

    where:
    failure                                          | thrown                                                                                         || attempts
    "the connection was refused"                     | neverReachedMandrill()                                                                         || 3
    "the connection timed out"                       | new ResourceAccessException("I/O error", new HttpConnectTimeoutException("HTTP connect timed out")) || 3
    "the connection was reset before any answer"     | new ResourceAccessException("Connection reset")                                                || 3
    "Mandrill or its edge answered with an error"    | new MandrillApiException("/messages/send.json", new MandrillError(502, null, null, null))      || 3
    "Mandrill did not answer in time"                | new ResourceAccessException("I/O error", new HttpTimeoutException("request timed out"))        || 1
    "the answer stalled or broke off after it began" | new RestClientException("Error while extracting response")                                    || 1
    "the answer could not be read"                   | new JsonSyntaxException("Expected BEGIN_ARRAY")                                                || 1
  }

  def "sendSystemEmail succeeds on a second attempt when the first one never reached Mandrill"() {
    given:
    def systemMessage = new MandrillMessage()

    when:
    def result = service.sendSystemEmail(systemMessage)

    then:
    1 * mandrillClient.send(systemMessage) >> { throw neverReachedMandrill() }

    then:
    1 * mandrillClient.send(systemMessage) >> [mandrillMessageStatus]
    result == true
  }

  def "sendSystemEmail throws when Mandrill returns rejected"() {
    given:
    def systemMessage = new MandrillMessage()
    def rejectedStatus = new MandrillMessageStatus()
    rejectedStatus.status = "rejected"

    when:
    service.sendSystemEmail(systemMessage)

    then:
    1 * mandrillClient.send(systemMessage) >> [rejectedStatus]
    thrown(EmailDeliveryException)
  }

  def "sendSystemEmail throws when Mandrill returns invalid"() {
    given:
    def systemMessage = new MandrillMessage()
    def invalidStatus = new MandrillMessageStatus()
    invalidStatus.status = "invalid"

    when:
    service.sendSystemEmail(systemMessage)

    then:
    1 * mandrillClient.send(systemMessage) >> [invalidStatus]
    thrown(EmailDeliveryException)
  }

  def "sendSystemEmail throws when a main recipient is rejected even though Mandrill lists a delivered copy first"() {
    given:
    def systemMessage = messageTo([recipient("desk@example.com", TO), recipient("copy@example.com", CC)])

    when:
    service.sendSystemEmail(systemMessage)

    then:
    1 * mandrillClient.send(systemMessage) >> [
        recipientStatus("copy@example.com", "sent"),
        recipientStatus("desk@example.com", "rejected")]
    thrown(EmailDeliveryException)
  }

  def "sendSystemEmail throws when the second of two main recipients is invalid"() {
    given:
    def systemMessage = messageTo([recipient("desk@example.com", TO), recipient("custody@example.com", TO)])

    when:
    service.sendSystemEmail(systemMessage)

    then:
    1 * mandrillClient.send(systemMessage) >> [
        recipientStatus("desk@example.com", "sent"),
        recipientStatus("custody@example.com", "invalid")]
    thrown(EmailDeliveryException)
  }

  def "sendSystemEmail counts the email as sent when only a copy recipient is rejected"() {
    given:
    def systemMessage = messageTo([
        recipient("desk@example.com", TO),
        recipient("Copy@Example.com", CC),
        recipient("audit@example.com", BCC)])

    when:
    def result = service.sendSystemEmail(systemMessage)

    then:
    1 * mandrillClient.send(systemMessage) >> [
        recipientStatus("copy@example.com", "rejected"),
        recipientStatus("audit@example.com", "invalid"),
        recipientStatus("desk@example.com", "queued")]
    result == true
  }

  def "sendSystemEmail throws when Mandrill gives a main recipient no status value"() {
    given:
    def systemMessage = messageTo([recipient("desk@example.com", TO)])

    when:
    service.sendSystemEmail(systemMessage)

    then:
    1 * mandrillClient.send(systemMessage) >> [recipientStatus("desk@example.com", null)]
    thrown(EmailDeliveryException)
  }

  def "sendSystemEmail throws when Mandrill answers for the copies but not for a main recipient"() {
    given:
    def systemMessage = messageTo([recipient("desk@example.com", TO), recipient("copy@example.com", CC)])

    when:
    service.sendSystemEmail(systemMessage)

    then:
    1 * mandrillClient.send(systemMessage) >> [recipientStatus("copy@example.com", "sent")]
    thrown(EmailDeliveryException)
  }

  def "sendSystemEmail throws when an address that is both a main and a copy recipient is rejected"() {
    given:
    def systemMessage = messageTo([recipient("desk@example.com", TO), recipient("desk@example.com", CC)])

    when:
    service.sendSystemEmail(systemMessage)

    then:
    1 * mandrillClient.send(systemMessage) >> [recipientStatus("desk@example.com", "rejected")]
    thrown(EmailDeliveryException)
  }

  def "sendSystemEmail matches a main recipient regardless of surrounding spaces and letter case"() {
    given:
    def systemMessage = messageTo([recipient(" Desk@Example.com ", TO)])

    when:
    def result = service.sendSystemEmail(systemMessage)

    then:
    1 * mandrillClient.send(systemMessage) >> [recipientStatus("desk@example.com", "sent")]
    result == true
  }

  def "sendSystemEmail throws when every recipient is a copy and Mandrill rejects them"() {
    given:
    def systemMessage = messageTo([recipient("copy@example.com", CC)])

    when:
    service.sendSystemEmail(systemMessage)

    then:
    1 * mandrillClient.send(systemMessage) >> [recipientStatus("copy@example.com", "rejected")]
    thrown(EmailDeliveryException)
  }

  def "sendSystemEmail throws when a rejected status names no recipient"() {
    given:
    def systemMessage = messageTo([recipient("desk@example.com", TO), recipient("copy@example.com", CC)])

    when:
    service.sendSystemEmail(systemMessage)

    then:
    1 * mandrillClient.send(systemMessage) >> [
        recipientStatus("desk@example.com", "sent"),
        recipientStatus(null, "rejected")]
    thrown(EmailDeliveryException)
  }

  def "sendSystemEmail returns false when Mandrill answers with no recipient status"() {
    given:
    def systemMessage = messageTo([recipient("desk@example.com", TO)])

    when:
    def result = service.sendSystemEmail(systemMessage)

    then:
    1 * mandrillClient.send(systemMessage) >> new MandrillMessageStatus[0]
    result == false
  }

  def "does not send email when the message has no recipient"() {
    given:
    def messageWithoutRecipient = service.newMandrillMessage(
        null, templateName, [:], ["test"], null)

    when:
    def response = service.send(user, messageWithoutRecipient, templateName)

    then:
    response == Optional.empty()
    0 * mandrillClient._
  }

  def "sends to the message recipient even when the user profile has no email"() {
    given:
    def userWithoutEmail = sampleUser().email(null).build()

    when:
    def response = service.send(userWithoutEmail, message, templateName)

    then:
    1 * mandrillClient.sendTemplate(templateName, message, null) >> [mandrillMessageStatus]
    response == Optional.of(mandrillMessageStatus)
  }

  def "returns empty when Mandrill returns empty response"() {
    when:
    def response = service.send(user, message, templateName)

    then:
    1 * mandrillClient.sendTemplate(templateName, message, null) >> new MandrillMessageStatus[0]
    response == Optional.empty()
  }

  def "can create new mandrill messages"() {
    when:
    MandrillMessage message = service.newMandrillMessage(
        user.email,
        templateName,
        [fname: user.firstName, lname: user.lastName],
        ["test"],
        null)
    then:
    message.to.first().email == user.email
    message.mergeVars.first().rcpt == user.email
    message.mergeVars.first().vars.first().name == "fname"
    message.mergeVars.first().vars.first().content == user.firstName
    message.mergeVars.first().vars.last().name == "lname"
    message.mergeVars.first().vars.last().content == user.lastName
    message.googleAnalyticsCampaign == templateName
    message.tags == ["test"]
    message.autoText == true
    message.preserveRecipients == true
    message.important == true
    message.trackClicks == true
    message.trackOpens == true
    message.googleAnalyticsDomains == ["tuleva.ee", "pension.tuleva.ee"]
  }

  def "can create new mandrill messages using the four-argument overload"() {
    when:
    MandrillMessage message = service.newMandrillMessage(
        user.email,
        templateName,
        [fname: user.firstName, lname: user.lastName],
        ["test"])
    then:
    message != null
    message.to.first().email == user.email
    message.attachments == []
  }

  def "can create new mandrill messages with a reply-to header"() {
    when:
    MandrillMessage message = service.newMandrillMessage(
        user.email,
        "reply@tuleva.ee",
        templateName,
        [fname: user.firstName, lname: user.lastName],
        ["test"],
        [])
    then:
    message != null
    message.headers == ["Reply-To": "reply@tuleva.ee"]
  }

  def "does not send email when the message has no recipient list at all"() {
    given:
    def messageWithoutTo = new MandrillMessage()

    when:
    def response = service.send(user, messageWithoutTo, templateName)

    then:
    response == Optional.empty()
    0 * mandrillClient._
  }

  private static ResourceAccessException neverReachedMandrill() {
    new ResourceAccessException("I/O error", new ConnectException("Connection refused"))
  }

  private static MandrillApiException mandrillError(String errorName) {
    new MandrillApiException("/messages/send.json", new MandrillError(500, -1, errorName, "simulated"))
  }

  private static MandrillMessage messageTo(List<Recipient> recipients) {
    new MandrillMessage().tap { to = recipients }
  }

  private static Recipient recipient(String email, Recipient.Type type) {
    new Recipient().tap {
      it.email = email
      it.type = type
    }
  }

  private static MandrillMessageStatus recipientStatus(String email, String status) {
    new MandrillMessageStatus().tap {
      it.email = email
      it.status = status
      it._id = email
    }
  }

}
