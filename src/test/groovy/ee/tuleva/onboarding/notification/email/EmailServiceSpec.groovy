package ee.tuleva.onboarding.notification.email

import com.microtripit.mandrillapp.lutung.MandrillApi
import com.microtripit.mandrillapp.lutung.controller.MandrillMessagesApi
import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.config.EmailConfiguration
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class EmailServiceSpec extends Specification {

  EmailConfiguration emailConfiguration = Mock()
  MandrillApi mandrillApi = Mock()
  EmailService service = new EmailService(emailConfiguration, mandrillApi)

  def setup() {
    emailConfiguration.from >> "tuleva@tuleva.ee"
    emailConfiguration.bcc >> "avaldused@tuleva.ee"
    emailConfiguration.mandrillKey >> Optional.of("")
  }

  def "Send fake email"() {
    given:
    User user = sampleUser().build()
    when:
    MandrillMessage message = service.newMandrillMessage(
      service.getRecipients(user),
      "subject",
      "html",
      ["test"],
      null)

    service.send(user, message)

    then:
    1 * mandrillApi.messages() >> mockMandrillMessageApi()
  }

  private MandrillMessagesApi mockMandrillMessageApi() {
    def messagesApi = Mock(MandrillMessagesApi)
    messagesApi.send(*_) >> ([Mock(MandrillMessageStatus)] as MandrillMessageStatus[])
    return messagesApi
  }
}
