package ee.tuleva.onboarding.notification.email

import ee.tuleva.onboarding.config.MandateEmailConfiguration
import org.junit.Ignore
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class EmailServiceSpec extends Specification {

    MandateEmailConfiguration mandateEmailConfiguration = Mock(MandateEmailConfiguration)
    EmailService service = new EmailService(mandateEmailConfiguration)

    def setup() {
        mandateEmailConfiguration.from >> "avaldused@tuleva.ee"
        mandateEmailConfiguration.bcc >> "avaldused@tuleva.ee"
        mandateEmailConfiguration.mandrillKey >> Optional.of("")

        service.initialize()
    }

    @Ignore
    def "Send mandate email"() {
        when:
        service.sendMandate(sampleUser().build(), 123, "file".bytes)

        then:
        true
    }

    @Ignore
    def "send member number email"() {
        when:
        service.sendMemberNumber(sampleUser().email("erko@risthein.ee").build())

        then:
        true
    }
}
