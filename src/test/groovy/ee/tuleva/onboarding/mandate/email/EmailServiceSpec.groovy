package ee.tuleva.onboarding.mandate.email

import ee.tuleva.onboarding.auth.UserFixture
import ee.tuleva.onboarding.config.MandateEmailConfiguration
import spock.lang.Ignore
import spock.lang.Specification

class EmailServiceSpec extends Specification {

    MandateEmailConfiguration mandateEmailConfiguration = Mock(MandateEmailConfiguration);
    EmailService service = new EmailService(mandateEmailConfiguration);

    def setup() {
        mandateEmailConfiguration.to >> "jordanvaldma@gmail.com"
        mandateEmailConfiguration.from >> "avaldused@tuleva.ee"
        mandateEmailConfiguration.bcc >> "avaldused@tuleva.ee"
        mandateEmailConfiguration.mandrillKey >> ""

        service.initialize()

    }

    @Ignore
    def "Send"() {
        given:
        String senderSignatureName = "Jordan Valdma";

        when:
        service.send(UserFixture.sampleUser(), 123, "file".bytes)

        then:
        true

    }

}
