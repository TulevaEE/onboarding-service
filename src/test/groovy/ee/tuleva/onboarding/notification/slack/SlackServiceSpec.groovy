package ee.tuleva.onboarding.notification.slack

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.core.env.Environment
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import spock.lang.Specification

import static org.mockito.Mockito.when
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR
import static ee.tuleva.onboarding.notification.slack.SlackService.SlackChannel
import static org.springframework.http.HttpMethod.POST
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(SlackService)
class SlackServiceSpec extends Specification {

  @Autowired
  SlackService slackService

  @Autowired
  MockRestServiceServer server

  @MockitoBean
  SlackWebhookConfiguration webhookConfiguration

  Environment environment = Mock()

  private String dummyWebhookUrl = "https://example.com"

  def setup() {
    slackService.environment = environment
  }

  def cleanup() {
    server.reset()
  }

  def "should send message to Slack if webhook URL is present"() {
    given:
    def testMessage = "Test Message 😎"

    when(webhookConfiguration.getWebhookUrl(SlackChannel.AML)).thenReturn(dummyWebhookUrl)

    server.expect(requestTo(dummyWebhookUrl))
        .andExpect(method(POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.text').value(testMessage))
        .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN))

    when:
    slackService.sendMessage(testMessage, AML)

    then:
    server.verify()
  }

  def "should send error message as a red attachment"() {
    given:
    def testMessage = "NAV publish still missing"

    when(webhookConfiguration.getWebhookUrl(SlackChannel.INVESTMENT)).thenReturn(dummyWebhookUrl)

    server.expect(requestTo(dummyWebhookUrl))
        .andExpect(method(POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.attachments[0].color').value("danger"))
        .andExpect(jsonPath('$.attachments[0].text').value(testMessage))
        .andExpect(jsonPath('$.attachments[0].fallback').value(testMessage))
        .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN))

    when:
    slackService.sendMessage(testMessage, INVESTMENT, ERROR)

    then:
    server.verify()
  }

  def "should not send message to Slack if webhook URL is not present and not in production"() {
    given:
    when(webhookConfiguration.getWebhookUrl(SlackChannel.AML)).thenReturn(null)
    environment.matchesProfiles("production") >> false


    when:
    slackService.sendMessage("Test Message 😎", AML)

    then:
    server.verify()
  }


  def "throws if webhook URL is not present and in production"() {
    given:
    when(webhookConfiguration.getWebhookUrl(SlackChannel.AML)).thenReturn(null)
    environment.matchesProfiles("production") >> true


    when:
    slackService.sendMessage("Test Message 😎", AML)

    then:
    thrown(IllegalStateException)
    server.verify()
  }
}
