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
  MandrillMessageStatus mandrillMessageStatus = Mock()

  def setup() {
    emailConfiguration.mandrillKey >> Optional.of("")
    mandrillApi.messages() >> mandrillMessagesApi
    mandrillMessageStatus.getStatus() >> "sent"
    mandrillMessageStatus.getId() >> "13"
  }

  def "Can send email with template"() {
    when:
    Optional<String> messageId = service.send(user, message, templateName)

    then:
    1 * mandrillMessagesApi.sendTemplate(templateName, [:], message, false, null, null) >> [mandrillMessageStatus]
    messageId == Optional.of("13")
  }

  def "Can send delayed emails with template"() {
    given:
    Instant sendAt = Instant.now()

    when:
    Optional<String> messageId = service.send(user, message, templateName, sendAt)

    then:
    1 * mandrillMessagesApi.sendTemplate(templateName, [:], message, false, null, Date.from(sendAt)) >> [mandrillMessageStatus]
    messageId == Optional.of("13")
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
}
