package ee.tuleva.onboarding.notification.email

import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import spock.lang.Ignore
import spock.lang.Specification

@Ignore
class EmailContentServiceSpec extends Specification {

    TemplateEngine templateEngine = Mock(TemplateEngine)
    EmailContentService emailContentService = new EmailContentService(templateEngine)

    def "get mandate email html content"() {
        given:
        String sampleContent = "<p>hello</p>";
        templateEngine.process("mandate_email", _ as Context) >> sampleContent
        when:
        String html = emailContentService.getMandateEmailHtml()
        then:
        html == sampleContent
    }

    def "get membership email html content"() {
        given:
        String sampleContent = "<p>hello</p>";
        templateEngine.process("mandate_email", _ as Context) >> sampleContent
        when:
        String html = emailContentService.getMembershipEmailHtml()
        then:
        html == sampleContent
    }

}
