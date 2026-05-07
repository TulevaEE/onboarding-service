package ee.tuleva.onboarding.notification.email

import com.microtripit.mandrillapp.lutung.MandrillApi
import com.microtripit.mandrillapp.lutung.controller.MandrillMessagesApi
import com.microtripit.mandrillapp.lutung.model.MandrillApiError
import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import com.microtripit.mandrillapp.lutung.view.MandrillScheduledMessageInfo
import ee.tuleva.onboarding.config.EmailConfiguration
import ee.tuleva.onboarding.user.User
import org.springframework.core.retry.RetryPolicy
import org.springframework.core.retry.RetryTemplate
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class EmailServiceSpec extends Specification {

  EmailConfiguration emailConfiguration = Mock()
  MandrillApi mandrillApi = Mock()
  MandrillMessagesApi mandrillMessagesApi = Mock()
  RetryTemplate retryTemplate = new RetryTemplate(
      RetryPolicy.builder()
          .includes(MandrillApiError, IOException)
          .maxRetries(2)
          .delay(Duration.ofMillis(1))
          .build())
  EmailService service = new EmailService(emailConfiguration, mandrillApi, retryTemplate)
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

  def setup() {
    emailConfiguration.mandrillKey >> Optional.of("")
    mandrillApi.messages() >> mandrillMessagesApi
  }

  def "Can send email with template"() {
    when:
    def response = service.send(user, message, templateName)

    then:
    1 * mandrillMessagesApi.sendTemplate(templateName, [:], message, false, null, null) >> [mandrillMessageStatus]
    response == Optional.of(mandrillMessageStatus)
  }

  def "Can send delayed emails with template"() {
    given:
    Instant sendAt = Instant.now()

    when:
    def response = service.send(user, message, templateName, sendAt)

    then:
    1 * mandrillMessagesApi.sendTemplate(templateName, [:], message, false, null, Date.from(sendAt)) >> [mandrillMessageStatus]
    response == Optional.of(mandrillMessageStatus)
  }

  def "Can cancel scheduled emails"() {
    given:
    String mandrillMessageId = "100"
    def info = new MandrillScheduledMessageInfo()
    1 * mandrillApi.messages().cancelScheduled(mandrillMessageId) >> info

    when:
    def scheduledEmailInfo = service.cancelScheduledEmail(mandrillMessageId)

    then:
    scheduledEmailInfo.get() == info
  }

  def "Can send system email without template"() {
    given:
    def systemMessage = new MandrillMessage()

    when:
    def result = service.sendSystemEmail(systemMessage)

    then:
    1 * mandrillMessagesApi.send(systemMessage, false) >> [mandrillMessageStatus]
    result == true
  }

  def "sendSystemEmail returns false when Mandrill not initialised"() {
    given:
    def serviceWithoutMandrill = new EmailService(emailConfiguration, null, retryTemplate)
    def systemMessage = new MandrillMessage()

    when:
    def result = serviceWithoutMandrill.sendSystemEmail(systemMessage)

    then:
    result == false
  }

  def "sendSystemEmail retries and returns false when all attempts throw IOException"() {
    given:
    def systemMessage = new MandrillMessage()

    when:
    def result = service.sendSystemEmail(systemMessage)

    then:
    3 * mandrillMessagesApi.send(systemMessage, false) >> { throw new IOException("Connection reset") }
    result == false
  }

  def "sendSystemEmail retries and returns false when all attempts throw MandrillApiError"() {
    given:
    def systemMessage = new MandrillMessage()

    when:
    def result = service.sendSystemEmail(systemMessage)

    then:
    3 * mandrillMessagesApi.send(systemMessage, false) >> { throw new MandrillApiError() }
    result == false
  }

  def "sendSystemEmail succeeds on second attempt after first fails"() {
    given:
    def systemMessage = new MandrillMessage()

    when:
    def result = service.sendSystemEmail(systemMessage)

    then:
    1 * mandrillMessagesApi.send(systemMessage, false) >> { throw new IOException("Connection reset") }

    then:
    1 * mandrillMessagesApi.send(systemMessage, false) >> [mandrillMessageStatus]
    result == true
  }

  def "does not send email when user has no email"() {
    given:
    def userWithoutEmail = sampleUser().email(null).build()

    when:
    def response = service.send(userWithoutEmail, message, templateName)

    then:
    response == Optional.empty()
    0 * mandrillMessagesApi._
  }

  def "returns empty when Mandrill returns empty response"() {
    when:
    def response = service.send(user, message, templateName)

    then:
    1 * mandrillMessagesApi.sendTemplate(templateName, [:], message, false, null, null) >> new MandrillMessageStatus[0]
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
  }
}
