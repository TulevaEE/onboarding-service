package ee.tuleva.onboarding.notification.email

import ee.tuleva.onboarding.config.MandateEmailConfiguration
import org.junit.Ignore
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class EmailServiceSpec extends Specification {

    MandateEmailConfiguration mandateEmailConfiguration = Mock(MandateEmailConfiguration)
    EmailContentService emailContentService = Mock(EmailContentService)
    EmailService service = new EmailService(mandateEmailConfiguration, emailContentService)

    def setup() {
        mandateEmailConfiguration.from >> "avaldused@tuleva.ee"
        mandateEmailConfiguration.bcc >> "avaldused@tuleva.ee"
        mandateEmailConfiguration.mandrillKey >> Optional.of("")

        service.initialize()
    }

    @Ignore
    def "Send mandate email"() {
        given:
        emailContentService.getMandateEmailHtml() >> "html"

        when:
        service.sendMandate(sampleUser().build(), 123, "file".bytes)

        then:
        true
    }

    @Ignore
    def "send member number email"() {
        given:
        emailContentService.getMembershipEmailHtml(_) >> "html"

        when:
        service.sendMemberNumber(sampleUser().email("erko@risthein.ee").build())

        then:
        true
    }
}
