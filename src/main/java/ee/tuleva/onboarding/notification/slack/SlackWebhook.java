package ee.tuleva.onboarding.notification.slack;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.UnknownContentTypeException;
import org.springframework.web.client.UnknownHttpStatusCodeException;

final class SlackWebhook {

  private static final Set<String> WEB_SCHEMES = Set.of("http", "https");

  private static final String REDACTED = "<webhook>";

  private static final String NO_ANSWER = "I/O error posting to Slack";

  private final URI uri;

  SlackWebhook(String url) {
    this.uri = webUri(url);
  }

  URI uri() {
    return uri;
  }

  RestClientException withoutUrl(RestClientException failure) {
    return switch (failure) {
      case RestClientResponseException answer -> answerWithoutUrl(answer);
      case UnknownContentTypeException unreadable -> unreadableWithoutUrl(unreadable);
      case ResourceAccessException noAnswer when noAnswer.getCause() instanceof IOException cause ->
          new ResourceAccessException(described(NO_ANSWER, cause), ioCauseWithoutUrl(cause));
      case ResourceAccessException noAnswer -> noAnswerWithoutUrl(noAnswer);
      default ->
          new RestClientException(
              described("Slack post failed", failure), causeWithoutUrl(failure.getCause()));
    };
  }

  private RestClientResponseException answerWithoutUrl(RestClientResponseException answer) {
    String message = describe(answer);
    HttpHeaders headers = headersWithoutUrl(answer.getResponseHeaders());
    byte[] body = bodyWithoutUrl(answer.getResponseBodyAsByteArray(), headers);
    Charset charset = charsetOf(headers);
    return switch (answer) {
      case HttpClientErrorException refusal ->
          HttpClientErrorException.create(
              message, refusal.getStatusCode(), refusal.getStatusText(), headers, body, charset);
      case HttpServerErrorException error ->
          HttpServerErrorException.create(
              message, error.getStatusCode(), error.getStatusText(), headers, body, charset);
      case UnknownHttpStatusCodeException unknown ->
          new UnknownHttpStatusCodeException(
              message,
              unknown.getStatusCode().value(),
              unknown.getStatusText(),
              headers,
              body,
              charset);
      default ->
          new RestClientResponseException(
              message, answer.getStatusCode(), answer.getStatusText(), headers, body, charset);
    };
  }

  private UnknownContentTypeException unreadableWithoutUrl(UnknownContentTypeException unreadable) {
    HttpHeaders headers = headersWithoutUrl(unreadable.getResponseHeaders());
    return new UnknownContentTypeException(
        unreadable.getTargetType(),
        unreadable.getContentType(),
        unreadable.getStatusCode(),
        unreadable.getStatusText(),
        headers,
        bodyWithoutUrl(unreadable.getResponseBody(), headers));
  }

  private ResourceAccessException noAnswerWithoutUrl(ResourceAccessException noAnswer) {
    ResourceAccessException rebuilt = new ResourceAccessException(described(NO_ANSWER, noAnswer));
    Throwable cause = causeWithoutUrl(noAnswer.getCause());
    if (cause != null) {
      rebuilt.initCause(cause);
    }
    return rebuilt;
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

  private HttpHeaders headersWithoutUrl(@Nullable HttpHeaders headers) {
    HttpHeaders scrubbedHeaders = new HttpHeaders();
    Optional.ofNullable(headers)
        .ifPresent(
            original ->
                original.forEach(
                    (name, values) ->
                        scrubbedHeaders.put(name, values.stream().map(this::scrubbed).toList())));
    return scrubbedHeaders;
  }

  private byte[] bodyWithoutUrl(byte[] body, HttpHeaders headers) {
    Charset charset = requireNonNullElse(charsetOf(headers), UTF_8);
    String text = new String(body, charset);
    return carriesSecret(text) ? scrubbed(text).getBytes(charset) : body;
  }

  private boolean names(Throwable failure) {
    StringWriter printed = new StringWriter();
    failure.printStackTrace(new PrintWriter(printed));
    return carriesSecret(printed.toString());
  }

  private boolean carriesSecret(String text) {
    return secrets().matcher(text).find();
  }

  private String scrubbed(String text) {
    return secrets().matcher(text).replaceAll(Matcher.quoteReplacement(REDACTED));
  }

  private Pattern secrets() {
    return Pattern.compile(secretForms().map(Pattern::quote).collect(joining("|")));
  }

  private Stream<String> secretForms() {
    String rawPath = uri.getRawPath();
    if (rawPath == null || rawPath.length() <= 1) {
      return Stream.of(uri.toString());
    }
    String path = uri.getPath();
    return Stream.of(rawPath, path, lastSegment(rawPath), lastSegment(path))
        .filter(form -> !form.isEmpty())
        .distinct();
  }

  private static String lastSegment(String path) {
    return path.substring(path.lastIndexOf('/') + 1);
  }

  private static URI webUri(String url) {
    URI parsed = parsed(url);
    if (!parsed.isAbsolute()
        || parsed.getHost() == null
        || !WEB_SCHEMES.contains(parsed.getScheme().toLowerCase(ROOT))) {
      throw new IllegalArgumentException("Slack webhook is not an http or https URL with a host");
    }
    return parsed;
  }

  private static URI parsed(String url) {
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

  private static @Nullable Charset charsetOf(HttpHeaders headers) {
    return Optional.ofNullable(headers.getContentType()).map(MediaType::getCharset).orElse(null);
  }
}
