package ee.tuleva.onboarding.notification.slack;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class SlackService implements OperationsNotificationService {

  private Environment environment;
  private final RestTemplate restTemplate;
  private final SlackWebhookConfiguration configuration;

  @Getter
  @RequiredArgsConstructor
  enum SlackChannel {
    AML("aml"),
    WITHDRAWALS("withdrawals"),
    CAPITAL_TRANSFER("capital_transfer"),
    INVESTMENT("investment"),
    SAVINGS("savings");

    private final String configurationKey;
  }

  public SlackService(
      RestTemplateBuilder restTemplateBuilder,
      SlackWebhookConfiguration configuration,
      Environment environment) {
    this.restTemplate = restTemplateBuilder.build();
    this.configuration = configuration;
    this.environment = environment;
  }

  @Override
  public void sendMessage(String message, Channel channel) {
    SlackChannel slackChannel = SlackChannel.valueOf(channel.name());
    String webhookUrl = configuration.getWebhookUrl(slackChannel);

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
