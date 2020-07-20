package ee.tuleva.onboarding.mandate.listener

import ee.tuleva.onboarding.conversion.ConversionResponseFixture
import ee.tuleva.onboarding.conversion.UserConversionService
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.contact.UserPreferences
import ee.tuleva.onboarding.mandate.email.MandateEmailService
import ee.tuleva.onboarding.user.User
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

@SpringBootTest
class MandateEmailSenderSpec extends Specification {
    @Autowired
    ApplicationEventPublisher publisher

    @SpringBean
    EpisService episService = Mock(EpisService)

    @SpringBean
    MandateEmailService mandateEmailService = Mock(MandateEmailService)

    @SpringBean
    UserConversionService userConversionService = Mock(UserConversionService)

    def "send email when second pillar mandate event was received" () {
        given:
        User user = sampleUser().build()

        SecondPillarMandateCreatedEvent event = new SecondPillarMandateCreatedEvent(
            user, 123, "123".bytes, Locale.ENGLISH
        )

        def conversion = ConversionResponseFixture.notFullyConverted()
        1 * userConversionService.getConversion(event.getUser()) >> conversion

        when:
        publisher.publishEvent(event)

        then:
        1 * mandateEmailService.sendSecondPillarMandate(user, 123, _, conversion, Locale.ENGLISH)
    }

    def "send email when third pillar mandate event was received" () {
        given:
        User user = sampleUser().build()

        UserPreferences contract = new UserPreferences()
        contract.setPensionAccountNumber("testPensionNumber")

        ThirdPillarMandateCreatedEvent event = new ThirdPillarMandateCreatedEvent(
            user, 123, "123".bytes, Locale.ENGLISH
        )
        1 * episService.getContactDetails(_) >> contract

        def conversion = ConversionResponseFixture.notFullyConverted()
        1 * userConversionService.getConversion(event.getUser()) >> conversion

        when:
        publisher.publishEvent(event)
        then:
        1 * mandateEmailService.sendThirdPillarMandate(user, 123, _, "testPensionNumber", conversion, Locale.ENGLISH)
    }
}
