package ee.tuleva.onboarding.notification.email

import com.microtripit.mandrillapp.lutung.MandrillApi
import com.microtripit.mandrillapp.lutung.controller.MandrillMessagesApi
import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import com.microtripit.mandrillapp.lutung.view.MandrillScheduledMessageInfo
import ee.tuleva.onboarding.config.EmailConfiguration
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class EmailServiceSpec extends Specification {

  EmailConfiguration emailConfiguration = Mock()
  MandrillApi mandrillApi = Mock()
  MandrillMessagesApi mandrillMessagesApi = Mock()
  EmailService service = new EmailService(emailConfiguration, mandrillApi)
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
    service.sendSystemEmail(systemMessage)

    then:
    1 * mandrillMessagesApi.send(systemMessage, false) >> [mandrillMessageStatus]
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
