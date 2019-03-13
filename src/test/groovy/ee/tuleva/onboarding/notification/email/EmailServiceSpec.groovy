package ee.tuleva.onboarding.notification.email

import com.microtripit.mandrillapp.lutung.MandrillApi
import com.microtripit.mandrillapp.lutung.controller.MandrillMessagesApi
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.config.EmailConfiguration
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class EmailServiceSpec extends Specification {

    EmailConfiguration mandateEmailConfiguration = Mock(EmailConfiguration)
    EmailContentService emailContentService = Mock(EmailContentService)
    MandrillApi mandrillApi = Mock(MandrillApi)
    EmailService service = new EmailService(mandateEmailConfiguration, emailContentService, mandrillApi)

    def setup() {
        mandateEmailConfiguration.from >> "tuleva@tuleva.ee"
        mandateEmailConfiguration.bcc >> "avaldused@tuleva.ee"
        mandateEmailConfiguration.mandrillKey >> Optional.of("")
    }

    def "Send mandate email"() {
        given:
        emailContentService.getMandateEmailHtml() >> "html"

        when:
        service.sendMandate(sampleUser().build(), 123, "file".bytes)

        then:
        1 * mandrillApi.messages() >> mockMandrillMessageApi()
    }

    def "send member number email"() {
        given:
        emailContentService.getMembershipEmailHtml(_) >> "html"

        when:
        service.sendMemberNumber(sampleUser().email("erko@risthein.ee").build())

        then:
        1 * mandrillApi.messages() >> mockMandrillMessageApi()
    }

    private MandrillMessagesApi mockMandrillMessageApi() {
        def messagesApi = Mock(MandrillMessagesApi)
        messagesApi.send(*_) >> ([Mock(MandrillMessageStatus)] as MandrillMessageStatus[])
        return messagesApi
    }
}
