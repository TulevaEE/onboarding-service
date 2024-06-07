package ee.tuleva.onboarding.notification.email.provider


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import spock.lang.Ignore
import spock.lang.Specification

@SpringBootTest
@ActiveProfiles("dev")
@Ignore
class MailchimpServiceTest extends Specification {

  @Autowired
  MailchimpService mailchimpService

  def "can send events"() {
    when:
    mailchimpService.sendEvent("test-email", "test-event")

    then:
    true
  }
}
