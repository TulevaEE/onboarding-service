package ee.tuleva.onboarding.notification.slack;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class AmlSlackService {

  @Value("${slack.webhook.url:#{null}}")
  private String webhookUrl;

  private final RestTemplate restTemplate;

  public AmlSlackService(RestTemplateBuilder restTemplateBuilder) {
    this.restTemplate = restTemplateBuilder.build();
  }

  public void sendMessage(String message) {
    if (webhookUrl == null) {
      log.info("Slack message: {}", message);
      return;
    }

    SlackMessage slackMessage = new SlackMessage(message);

    restTemplate.postForEntity(webhookUrl, new HttpEntity<>(slackMessage), String.class);
  }

  private record SlackMessage(String text) {}
}
