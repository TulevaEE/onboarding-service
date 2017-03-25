package ee.tuleva.onboarding.mandate.email

import spock.lang.Ignore
import spock.lang.Specification

class EmailServiceSpec extends Specification {

    EmailService service = new EmailService();

    @Ignore
    def "Send"() {
        given:
        String senderSignatureName = "Jordan Valdma";

        when:
        service.send(senderSignatureName)

        then:
        true

    }

}
