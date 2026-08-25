package ee.tuleva.onboarding.notification.slack;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@NullMarked
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
    sendMessage(message, channel, Severity.INFO);
  }

  @Override
  public void sendMessage(String message, Channel channel, Severity severity) {
    SlackChannel slackChannel = SlackChannel.valueOf(channel.name());
    String webhookUrl = configuration.getWebhookUrl(slackChannel);

    if (webhookUrl == null) {
      if (environment.matchesProfiles("production")) {
        throw new IllegalStateException("No webhook for slack channel " + channel);
      }
      log.info("Slack message for channel {}: {}", channel, message);
      return;
    }

    restTemplate.postForEntity(
        webhookUrl, new HttpEntity<>(payload(message, severity)), String.class);
  }

  private static Object payload(String message, Severity severity) {
    return switch (severity) {
      case INFO -> new SlackMessage(message);
      case ERROR -> new SlackAlert(List.of(new Attachment("danger", message, message)));
    };
  }

  private record SlackMessage(String text) {}

  private record SlackAlert(List<Attachment> attachments) {}

  private record Attachment(String color, String text, String fallback) {}
}
