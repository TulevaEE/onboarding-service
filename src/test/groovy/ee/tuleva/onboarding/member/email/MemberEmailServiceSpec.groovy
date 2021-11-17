package ee.tuleva.onboarding.member.email

import com.microtripit.mandrillapp.lutung.MandrillApi
import com.microtripit.mandrillapp.lutung.controller.MandrillMessagesApi
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.config.EmailConfiguration
import ee.tuleva.onboarding.notification.email.EmailService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class MemberEmailServiceSpec extends Specification {

  EmailConfiguration emailConfiguration = Mock(EmailConfiguration)
  MemberEmailContentService emailContentService = Mock(MemberEmailContentService)
  MandrillApi mandrillApi = Mock(MandrillApi)
  EmailService service = new EmailService(emailConfiguration, mandrillApi)
  MemberEmailService memberService = new MemberEmailService(service, emailContentService)

  def setup() {
    emailConfiguration.from >> "tuleva@tuleva.ee"
    emailConfiguration.bcc >> "avaldused@tuleva.ee"
    emailConfiguration.mandrillKey >> Optional.of("")
  }

  def "send member number email"() {
    given:
    emailContentService.getMembershipEmailHtml(_) >> "html"

    when:
    memberService.sendMemberNumber(sampleUser().email("erko@risthein.ee").build(), Locale.ENGLISH)

    then:
    1 * mandrillApi.messages() >> mockMandrillMessageApi()
  }

  private MandrillMessagesApi mockMandrillMessageApi() {
    def messagesApi = Mock(MandrillMessagesApi)
    messagesApi.send(*_) >> ([Mock(MandrillMessageStatus)] as MandrillMessageStatus[])
    return messagesApi
  }
}
