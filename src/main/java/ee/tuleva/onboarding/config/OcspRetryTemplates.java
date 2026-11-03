package ee.tuleva.onboarding.config;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static org.springframework.http.HttpStatus.REQUEST_TIMEOUT;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

@Slf4j
final class OcspRetryTemplates {

  private OcspRetryTemplates() {}

  static RetryTemplate askingAgainAtOnce() {
    final int RETRIES_WHILE_THE_PERSON_WAITS = 2;
    final Duration DELAY_BEFORE_ASKING_AGAIN = Duration.ofMillis(200);
    final Duration RETRY_WINDOW_SHORTER_THAN_EITHER_OCSP_TIMEOUT = Duration.ofSeconds(2);
    var retryTemplate =
        new RetryTemplate(
            RetryPolicy.builder()
                .includes(RestClientException.class)
                .predicate(failure -> isWorthAskingAgainAtOnce(failure))
                .maxRetries(RETRIES_WHILE_THE_PERSON_WAITS)
                .delay(DELAY_BEFORE_ASKING_AGAIN)
                .timeout(RETRY_WINDOW_SHORTER_THAN_EITHER_OCSP_TIMEOUT)
                .build());
    retryTemplate.setRetryListener(new LoggingEachOcspRetry());
    return retryTemplate;
  }

  private static boolean isWorthAskingAgainAtOnce(Throwable failure) {
    return switch (failure) {
      case HttpStatusCodeException answer when namesHowLongToWait(answer) -> false;
      case HttpClientErrorException refusal ->
          refusal.getStatusCode().isSameCodeAs(REQUEST_TIMEOUT)
              || refusal.getStatusCode().isSameCodeAs(TOO_MANY_REQUESTS);
      default -> true;
    };
  }

  private static boolean namesHowLongToWait(HttpStatusCodeException answer) {
    HttpHeaders headers = answer.getResponseHeaders();
    return headers != null && headers.containsHeader(RETRY_AFTER);
  }

  private static class LoggingEachOcspRetry implements RetryListener {
    @Override
    public void beforeRetry(
        RetryPolicy retryPolicy, Retryable<?> retryable, RetryState retryState) {
      log.warn(
          "OCSP responder failed, asking again: attempt={}, reason={}",
          retryState.getRetryCount() + 1,
          getMostSpecificCause(retryState.getLastException()).getClass().getSimpleName());
    }
  }
}
