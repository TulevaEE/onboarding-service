package ee.tuleva.onboarding.notification.slack;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class SlackService {

  // mapped from slack.webhooks.___
  public enum SlackChannel {
    AML("aml"),
    WITHDRAWALS("withdrawals");

    @Getter
    private final String configurationKey;

    SlackChannel(String configurationKey) {
      this.configurationKey = configurationKey;
    }
  }

  private final RestTemplate restTemplate;
  private final SlackWebhookConfiguration configuration;

  public SlackService(RestTemplateBuilder restTemplateBuilder, SlackWebhookConfiguration configuration) {
    this.restTemplate = restTemplateBuilder.build();
    this.configuration = configuration;
  }

  public void sendMessage(String message, SlackChannel channel) {
    String webhookUrl = configuration.getWebhookUrl(channel);

    if (webhookUrl == null) {
      log.info("Slack message for channel {}: {}", channel, message);
      return;
    }

    SlackMessage slackMessage = new SlackMessage(message);

    restTemplate.postForEntity(webhookUrl, new HttpEntity<>(slackMessage), String.class);
  }

  private record SlackMessage(String text) {
  }
}
