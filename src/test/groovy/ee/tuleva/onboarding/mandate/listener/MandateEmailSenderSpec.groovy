package ee.tuleva.onboarding.mandate.listener

import ee.tuleva.onboarding.mandate.email.MandateEmailService
import ee.tuleva.onboarding.user.User
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import java.util.concurrent.TimeUnit

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

@SpringBootTest
class MandateEmailSenderSpec extends Specification {
    @Autowired
    ApplicationEventPublisher publisher

    @SpringBean
    MandateEmailService mandateEmailService = Mock(MandateEmailService)

    def "send email when second pillar mandate event was received" () {
        given:
        User user = sampleUser().build()
        SecondPillarMandateCreatedEvent event = new SecondPillarMandateCreatedEvent(user, 123, "123".bytes)
        when:
        publisher.publishEvent(event)
        TimeUnit.SECONDS.sleep(1)
        then:
        1 * mandateEmailService.sendSecondPillarMandate(user, 123, _)
    }

    def "send email when third pillar mandate event was received" () {
        given:
        User user = sampleUser().build()
        ThirdPillarMandateCreatedEvent event = new ThirdPillarMandateCreatedEvent(user, 123, "123".bytes, "123")
        when:
        publisher.publishEvent(event)
        TimeUnit.SECONDS.sleep(1)
        then:
        1 * mandateEmailService.sendThirdPillarMandate(user, 123, _, "123")
    }
}
