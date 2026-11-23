package ee.tuleva.onboarding.notification.slack;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.env.Environment;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@NullMarked
@Service
@Slf4j
public class SlackService implements OperationsNotificationService {

  private final Environment environment;
  private final RestClient restClient;
  private final SlackWebhookConfiguration configuration;

  @Getter
  @RequiredArgsConstructor
  enum SlackChannel {
    AML("aml"),
    WITHDRAWALS("withdrawals"),
    CAPITAL_TRANSFER("capital_transfer"),
    INVESTMENT("investment"),
    SAVINGS("savings"),
    TRADING("trading");

    private final String configurationKey;

    static SlackChannel of(Channel channel) {
      return switch (channel) {
        case AML -> AML;
        case WITHDRAWALS -> WITHDRAWALS;
        case CAPITAL_TRANSFER -> CAPITAL_TRANSFER;
        case INVESTMENT -> INVESTMENT;
        case SAVINGS -> SAVINGS;
        case TRADING -> TRADING;
      };
    }
  }

  @Autowired
  public SlackService(
      SlackWebhookConfiguration configuration,
      Environment environment,
      ClientHttpRequestFactoryBuilder<?> requestFactoryBuilder,
      HttpClientSettings httpClientSettings) {
    this(
        restClientOutsideTheSharedSetup(requestFactoryBuilder, httpClientSettings).build(),
        configuration,
        environment);
  }

  SlackService(
      RestClient restClient, SlackWebhookConfiguration configuration, Environment environment) {
    this.restClient = restClient;
    this.configuration = configuration;
    this.environment = environment;
  }

  static RestClient.Builder restClientOutsideTheSharedSetup(
      ClientHttpRequestFactoryBuilder<?> requestFactoryBuilder,
      HttpClientSettings httpClientSettings) {
    return RestClient.builder()
        .requestFactory(
            new BufferingClientHttpRequestFactory(requestFactoryBuilder.build(httpClientSettings)));
  }

  @Override
  public void sendMessage(String message, Channel channel) {
    sendMessage(message, channel, Severity.INFO);
  }

  @Override
  public void sendMessage(String message, Channel channel, Severity severity) {
    SlackChannel slackChannel = SlackChannel.of(channel);
    Optional<String> webhookUrl =
        Optional.ofNullable(configuration.getWebhookUrl(slackChannel))
            .map(String::strip)
            .filter(url -> !url.isEmpty());

    if (webhookUrl.isEmpty()) {
      if (environment.matchesProfiles("production")) {
        throw new IllegalStateException("No webhook for slack channel " + channel);
      }
      log.info("Slack message for channel {}: {}", channel, message);
      return;
    }

    post(webhookUrl.get(), payload(message, severity));
  }

  private void post(String webhookUrl, Object payload) {
    SlackWebhook webhook = new SlackWebhook(webhookUrl);
    try {
      restClient.post().uri(webhook.uri()).body(payload).retrieve().toBodilessEntity();
    } catch (RestClientException failure) {
      throw webhook.withoutUrl(failure);
    }
  }

  private static Object payload(String message, Severity severity) {
    return switch (severity) {
      case INFO -> new SlackMessage(message);
      case WARNING -> new SlackAlert(List.of(new Attachment("warning", message, message)));
      case ERROR -> new SlackAlert(List.of(new Attachment("danger", message, message)));
    };
  }

  private record SlackMessage(String text) {}

  private record SlackAlert(List<Attachment> attachments) {}

  private record Attachment(String color, String text, String fallback) {}
}
