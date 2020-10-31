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
        def tags = ["mandate"]

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
        def tags = ["mandate"]

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
    def "mandate tagging 2nd pillar"() {
        given:
        def pillarSuggestion = new SecondPillarSuggestion(isThirdPillarActive, isFullyConverted, isMember)

        when:
        def tags = mandateEmailService.getMandateTags(pillarSuggestion)

        then:
        tags == expectedTags

        where:
        isThirdPillarActive | isFullyConverted | isMember || expectedTags
        false               | false            | false    || ["mandate", "suggest_member"]
        false               | false            | true     || ["mandate"]
        true                | false            | false    || ["mandate", "suggest_3"]
        true                | false            | true     || ["mandate"]
        true                | true             | false    || ["mandate", "suggest_member"]
        true                | true             | true     || ["mandate"]
    }

    @Unroll
    def "mandate tagging 3rd pillar"() {
        given:
        def pillarSuggestion = new ThirdPillarSuggestion(isSecondPillarActive, isFullyConverted, isMember)

        when:
        def tags = mandateEmailService.getMandateTags(pillarSuggestion)

        then:
        tags == expectedTags

        where:
        isSecondPillarActive | isFullyConverted | isMember || expectedTags
        false                | false            | false    || ["mandate", "suggest_member"]
        false                | false            | true     || ["mandate"]
        true                 | false            | false    || ["mandate", "suggest_2"]
        true                 | false            | true     || ["mandate"]
        true                 | true             | false    || ["mandate", "suggest_member"]
        true                 | true             | true     || ["mandate"]
    }
}
