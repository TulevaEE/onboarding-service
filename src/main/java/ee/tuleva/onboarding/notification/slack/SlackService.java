package ee.tuleva.onboarding.notification.slack;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class SlackService {

  private Environment environment;

  private final RestTemplate restTemplate;
  private final SlackWebhookConfiguration configuration;

  // mapped from slack.webhooks.___ in application properties
  public enum SlackChannel {
    AML("aml"),
    WITHDRAWALS("withdrawals"),
    CAPITAL_TRANSFER("capital_transfer");

    @Getter private final String configurationKey;

    SlackChannel(String configurationKey) {
      this.configurationKey = configurationKey;
    }
  }

  public SlackService(
      RestTemplateBuilder restTemplateBuilder,
      SlackWebhookConfiguration configuration,
      Environment environment) {
    this.restTemplate = restTemplateBuilder.build();
    this.configuration = configuration;
    this.environment = environment;
  }

  public void sendMessage(String message, SlackChannel channel) {
    String webhookUrl = configuration.getWebhookUrl(channel);

    if (webhookUrl == null) {
      if (environment.matchesProfiles("production")) {
        throw new IllegalStateException("No webhook for slack channel " + channel);
      }
      log.info("Slack message for channel {}: {}", channel, message);
      return;
    }

    SlackMessage slackMessage = new SlackMessage(message);

    restTemplate.postForEntity(webhookUrl, new HttpEntity<>(slackMessage), String.class);
  }

  private record SlackMessage(String text) {}
}
