package ee.tuleva.onboarding.mandate.email

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import ee.tuleva.onboarding.notification.email.EmailService
import spock.lang.Specification
import spock.lang.Unroll

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture

class MandateEmailServiceSpec extends Specification {

    MandateEmailContentService emailContentService = Mock(MandateEmailContentService)
    EmailService emailService = Mock(EmailService)
    MandateEmailService mandateEmailService = new MandateEmailService(emailService, emailContentService)

    def "Send second pillar mandate email"() {
        given:
        def user = sampleUser().build()
        def conversion = notFullyConverted()
        def contactDetails = contactDetailsFixture()
        def recipients = [new Recipient()]
        def message = new MandrillMessage()
        def subject = mandateEmailService.getMandateEmailSubject()
        def html = "html"
        def tags = ["mandate", "pillar_2", "suggest_3"]

        emailContentService.getSecondPillarHtml(*_) >> html
        emailService.getRecipients(user) >> recipients

        when:
        mandateEmailService.sendSecondPillarMandate(
            user, 123, "file".bytes, conversion, contactDetails, Locale.ENGLISH)

        then:
        1 * emailService.newMandrillMessage(recipients, subject, html, tags, _) >> message
        1 * emailService.send(user, message)
    }

    def "Send third pillar mandate email"() {
        given:
        def user = sampleUser().build()
        def conversion = notFullyConverted()
        def contactDetails = contactDetailsFixture()
        def recipients = [new Recipient()]
        def message = new MandrillMessage()
        def subject = mandateEmailService.getMandateEmailSubject()
        def html = "html"
        def tags = ["mandate", "pillar_3", "suggest_2"]

        emailContentService.getThirdPillarHtml(*_) >> html
        emailService.getRecipients(user) >> recipients

        when:
        mandateEmailService.sendThirdPillarMandate(
            user, 123, "file".bytes, conversion, contactDetails, Locale.ENGLISH)

        then:
        1 * emailService.newMandrillMessage(recipients, subject, html, tags, _) >> message
        1 * emailService.send(user, message)
    }

    @Unroll
    def "mandate tagging for 2nd pillar mandates"() {
        given:
        def pillarSuggestion = new PillarSuggestion(3, isThirdPillarActive, isThirdPillarFullyConverted, isMember)

        when:
        def tags = mandateEmailService.getMandateTags(pillarSuggestion)

        then:
        tags == expectedTags

        where:
        isThirdPillarActive | isThirdPillarFullyConverted | isMember || expectedTags
        false               | false                       | false    || ["mandate", "pillar_2", "suggest_3"]
        false               | false                       | true     || ["mandate", "pillar_2", "suggest_3"]
        true                | false                       | false    || ["mandate", "pillar_2", "suggest_3"]
        true                | false                       | true     || ["mandate", "pillar_2", "suggest_3"]
        true                | true                        | false    || ["mandate", "pillar_2", "suggest_member"]
        true                | true                        | true     || ["mandate", "pillar_2"]
    }

    @Unroll
    def "mandate tagging for 3rd pillar mandates"() {
        given:
        def pillarSuggestion = new PillarSuggestion(2, isSecondPillarActive, isSecondPillarFullyConverted, isMember)

        when:
        def tags = mandateEmailService.getMandateTags(pillarSuggestion)

        then:
        tags == expectedTags

        where:
        isSecondPillarActive | isSecondPillarFullyConverted | isMember || expectedTags
        false                | false                        | false    || ["mandate", "pillar_3", "suggest_2"]
        false                | false                        | true     || ["mandate", "pillar_3", "suggest_2"]
        true                 | false                        | false    || ["mandate", "pillar_3", "suggest_2"]
        true                 | false                        | true     || ["mandate", "pillar_3", "suggest_2"]
        true                 | true                         | false    || ["mandate", "pillar_3", "suggest_member"]
        true                 | true                         | true     || ["mandate", "pillar_3"]
    }
}
