package ee.tuleva.onboarding.notification.slack;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNullElse;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
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
    SlackChannel slackChannel = SlackChannel.of(channel);
    String webhookUrl = configuration.getWebhookUrl(slackChannel);

    if (webhookUrl == null) {
      if (environment.matchesProfiles("production")) {
        throw new IllegalStateException("No webhook for slack channel " + channel);
      }
      log.info("Slack message for channel {}: {}", channel, message);
      return;
    }

    post(webhookUrl, payload(message, severity));
  }

  private void post(String webhookUrl, Object payload) {
    try {
      restTemplate.postForEntity(webhookUrl, new HttpEntity<>(payload), String.class);
    } catch (RestClientException failure) {
      throw withoutWebhookUrl(failure);
    }
  }

  private static RestClientException withoutWebhookUrl(RestClientException failure) {
    return switch (failure) {
      case HttpClientErrorException refusal ->
          HttpClientErrorException.create(
              describe(refusal),
              refusal.getStatusCode(),
              refusal.getStatusText(),
              headersOf(refusal),
              refusal.getResponseBodyAsByteArray(),
              charsetOf(refusal));
      case HttpServerErrorException error ->
          HttpServerErrorException.create(
              describe(error),
              error.getStatusCode(),
              error.getStatusText(),
              headersOf(error),
              error.getResponseBodyAsByteArray(),
              charsetOf(error));
      case ResourceAccessException noAnswer when noAnswer.getCause() instanceof IOException cause ->
          new ResourceAccessException("I/O error posting to Slack: " + cause.getMessage(), cause);
      default ->
          new RestClientException(
              "Slack post failed: failure=" + failure.getClass().getSimpleName());
    };
  }

  private static String describe(RestClientResponseException answer) {
    return "Slack answered with an error: status="
        + answer.getStatusCode().value()
        + " "
        + answer.getStatusText()
        + ", body="
        + LogFormatUtils.formatValue(answer.getResponseBodyAsString(UTF_8), -1, true);
  }

  private static HttpHeaders headersOf(RestClientResponseException answer) {
    return requireNonNullElse(answer.getResponseHeaders(), HttpHeaders.EMPTY);
  }

  private static @Nullable Charset charsetOf(RestClientResponseException answer) {
    return Optional.ofNullable(headersOf(answer).getContentType())
        .map(MediaType::getCharset)
        .orElse(null);
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
