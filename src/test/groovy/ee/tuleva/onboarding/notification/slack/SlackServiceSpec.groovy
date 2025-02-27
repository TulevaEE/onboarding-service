package ee.tuleva.onboarding.notification.slack

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import spock.lang.Specification

import static org.springframework.http.HttpMethod.POST
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(SlackService)
class SlackServiceSpec extends Specification {

  @Autowired
  SlackService slackService

  @Autowired
  MockRestServiceServer server

  private String dummyWebhookUrl = "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX"

  def cleanup() {
    slackService.webhookUrl = null
    server.reset()
  }

  def "should send message to Slack if webhook URL is present"() {
    given:
    slackService.webhookUrl = dummyWebhookUrl
    def testMessage = "Test Message 😎"

    server.expect(requestTo(dummyWebhookUrl))
        .andExpect(method(POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.text').value(testMessage))
        .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN))

    when:
    slackService.sendMessage(testMessage)

    then:
    server.verify()
  }

  def "should not send message to Slack if webhook URL is not present"() {
    given:
    slackService.webhookUrl = null

    when:
    slackService.sendMessage("Test Message 😎")

    then:
    server.verify()
  }
}
