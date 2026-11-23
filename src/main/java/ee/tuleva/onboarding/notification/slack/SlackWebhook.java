package ee.tuleva.onboarding.notification.slack;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNullElse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.UnknownHttpStatusCodeException;

final class SlackWebhook {

  private static final String SCHEME_SEPARATOR = "://";

  private static final String REDACTED = "<webhook>";

  private final String url;

  SlackWebhook(String url) {
    this.url = url;
  }

  URI uri() {
    try {
      return new URI(url);
    } catch (URISyntaxException invalid) {
      throw new IllegalArgumentException(
          "Slack webhook is not a valid URI: reason="
              + invalid.getReason()
              + ", index="
              + invalid.getIndex());
    }
  }

  RestClientException withoutUrl(RestClientException failure) {
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
      case UnknownHttpStatusCodeException unknown ->
          new UnknownHttpStatusCodeException(
              describe(unknown),
              unknown.getStatusCode().value(),
              unknown.getStatusText(),
              headersOf(unknown),
              unknown.getResponseBodyAsByteArray(),
              charsetOf(unknown));
      case RestClientResponseException answer ->
          new RestClientResponseException(
              describe(answer),
              answer.getStatusCode(),
              answer.getStatusText(),
              headersOf(answer),
              answer.getResponseBodyAsByteArray(),
              charsetOf(answer));
      case ResourceAccessException noAnswer when noAnswer.getCause() instanceof IOException cause ->
          new ResourceAccessException(
              described("I/O error posting to Slack", cause), ioCauseWithoutUrl(cause));
      default ->
          new RestClientException(
              described("Slack post failed", failure), causeWithoutUrl(failure.getCause()));
    };
  }

  private String describe(RestClientResponseException answer) {
    return "Slack answered with an error: status="
        + answer.getStatusCode().value()
        + " "
        + answer.getStatusText()
        + ", body="
        + scrubbed(LogFormatUtils.formatValue(answer.getResponseBodyAsString(UTF_8), -1, true));
  }

  private String described(String description, Throwable failure) {
    String named = description + ": failure=" + failure.getClass().getSimpleName();
    return Optional.ofNullable(failure.getMessage())
        .map(message -> named + ", message=" + scrubbed(message))
        .orElse(named);
  }

  private IOException ioCauseWithoutUrl(IOException cause) {
    return names(cause) ? replacementFor(cause) : cause;
  }

  private @Nullable Throwable causeWithoutUrl(@Nullable Throwable cause) {
    return cause != null && names(cause) ? replacementFor(cause) : cause;
  }

  private IOException replacementFor(Throwable cause) {
    return new IOException(described("Replaced a cause that named the Slack webhook", cause));
  }

  private boolean names(Throwable failure) {
    StringWriter printed = new StringWriter();
    failure.printStackTrace(new PrintWriter(printed));
    return printed.toString().contains(secret());
  }

  private String scrubbed(String text) {
    return text.replace(secret(), REDACTED);
  }

  private String secret() {
    return pathOf(url).orElse(url);
  }

  private static Optional<String> pathOf(String url) {
    int schemeEnd = url.indexOf(SCHEME_SEPARATOR);
    int pathStart = url.indexOf('/', schemeEnd < 0 ? 0 : schemeEnd + SCHEME_SEPARATOR.length());
    return pathStart < 0 || pathStart == url.length() - 1
        ? Optional.empty()
        : Optional.of(url.substring(pathStart));
  }

  private static HttpHeaders headersOf(RestClientResponseException answer) {
    return requireNonNullElse(answer.getResponseHeaders(), HttpHeaders.EMPTY);
  }

  private static @Nullable Charset charsetOf(RestClientResponseException answer) {
    return Optional.ofNullable(headersOf(answer).getContentType())
        .map(MediaType::getCharset)
        .orElse(null);
  }
}
