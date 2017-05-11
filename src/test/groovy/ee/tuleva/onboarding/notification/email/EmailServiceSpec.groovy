package ee.tuleva.onboarding.notification.email

import ee.tuleva.onboarding.config.MandateEmailConfiguration
import org.junit.Ignore
import spock.lang.Specification

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
        service.sendMandate(ee.tuleva.onboarding.auth.UserFixture.sampleUser().build(), 123, "file".bytes)

        then:
        true
    }

    @Ignore
    def "send member number email"() {
        when:
        service.sendMemberNumber(ee.tuleva.onboarding.auth.UserFixture.sampleUser().email("erko@risthein.ee").build())

        then:
        true
    }
}
