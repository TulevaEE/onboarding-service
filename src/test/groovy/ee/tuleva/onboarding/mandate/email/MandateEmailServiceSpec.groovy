package ee.tuleva.onboarding.mandate.email

import com.microtripit.mandrillapp.lutung.MandrillApi
import com.microtripit.mandrillapp.lutung.controller.MandrillMessagesApi
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.config.EmailConfiguration
import ee.tuleva.onboarding.notification.email.EmailService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class MandateEmailServiceSpec extends Specification {

    EmailConfiguration emailConfiguration = Mock(EmailConfiguration)
    MandateEmailContentService emailContentService = Mock(MandateEmailContentService)
    MandrillApi mandrillApi = Mock(MandrillApi)
    EmailService service = new EmailService(emailConfiguration, mandrillApi)
    MandateEmailService mandateService = new MandateEmailService(service, emailContentService)

    def setup() {
        emailConfiguration.from >> "tuleva@tuleva.ee"
        emailConfiguration.bcc >> "avaldused@tuleva.ee"
        emailConfiguration.mandrillKey >> Optional.of("")
    }

    def "Send second pillar mandate email"() {
        given:
        emailContentService.getSecondPillarHtml(Locale.ENGLISH) >> "html"

        when:
        mandateService.sendSecondPillarMandate(sampleUser().build(), 123, "file".bytes, Locale.ENGLISH)

        then:
        1 * mandrillApi.messages() >> mockMandrillMessageApi()
    }

    def "Send third pillar mandate email"() {
        given:
        emailContentService.getThirdPillarHtml("123", Locale.ENGLISH) >> "html"

        when:
        mandateService.sendThirdPillarMandate(
            sampleUser().build(), 123, "file".bytes, "123", Locale.ENGLISH
        )

        then:
        1 * mandrillApi.messages() >> mockMandrillMessageApi()
    }

    private MandrillMessagesApi mockMandrillMessageApi() {
        def messagesApi = Mock(MandrillMessagesApi)
        messagesApi.send(*_) >> ([Mock(MandrillMessageStatus)] as MandrillMessageStatus[])
        return messagesApi
    }
}
