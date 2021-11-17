package ee.tuleva.onboarding.notification.email

import com.microtripit.mandrillapp.lutung.MandrillApi
import com.microtripit.mandrillapp.lutung.controller.MandrillMessagesApi
import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
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
  MandrillMessage message = service.newMandrillMessage(
      service.getRecipients(user),
      "subject",
      "html",
      ["test"],
      null)
  MandrillMessageStatus mandrillMessageStatus = Mock()

  def setup() {
    emailConfiguration.from >> "tuleva@tuleva.ee"
    emailConfiguration.bcc >> "avaldused@tuleva.ee"
    emailConfiguration.mandrillKey >> Optional.of("")
    mandrillApi.messages() >> mandrillMessagesApi
    mandrillMessageStatus.getStatus() >> "sent"
    mandrillMessageStatus.getId() >> "13"
  }

  def "Send fake email"() {
    when:
    Optional<String> messageId = service.send(user, message)

    then:
    1 * mandrillMessagesApi.send(message, false, null, null) >> [mandrillMessageStatus]
    messageId == Optional.of("13")
  }

  def "Send delayed email"() {
    given:
    Instant sendAt = Instant.now();

    when:
    Optional<String> messageId = service.send(user, message, sendAt)

    then:
    1 * mandrillMessagesApi.send(message, false, null, Date.from(sendAt)) >> [mandrillMessageStatus]
    messageId == Optional.of("13")
  }

  def "cancels scheduled email"() {
    given:
    String mandrillMessageId = "100"

    when:
    service.cancelScheduledEmail(mandrillMessageId)

    then:
    1 * mandrillApi.messages().cancelScheduled(mandrillMessageId)
  }
}
