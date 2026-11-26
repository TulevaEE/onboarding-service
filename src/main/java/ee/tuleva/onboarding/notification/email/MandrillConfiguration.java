package ee.tuleva.onboarding.notification.email;

import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;

@Configuration
@Slf4j
class MandrillConfiguration {

  private static final Duration FIRST_RETRY_DELAY = Duration.ofMillis(200);

  @Bean
  @Nullable MandrillClient mandrillClient(
      @Value("${mandrill.url}") String mandrillUrl,
      @Value("${mandrill.key:#{null}}") @Nullable String mandrillKey) {
    if (mandrillKey == null) {
      log.warn("Mandrill key not present, emails will not be sent: mandrillUrl={}", mandrillUrl);
      return null;
    }
    return MandrillClient.using(mandrillKey, URI.create(mandrillUrl));
  }

  @Bean
  static RetryTemplate emailServiceRetryTemplate() {
    return emailServiceRetryTemplateWithFirstDelay(FIRST_RETRY_DELAY);
  }

  static RetryTemplate emailServiceRetryTemplateWithFirstDelay(Duration firstRetryDelay) {
    var policy =
        RetryPolicy.builder()
            .predicate(MandrillConfiguration::mayBeSentAgain)
            .maxRetries(2)
            .delay(firstRetryDelay)
            .multiplier(3)
            .maxDelay(Duration.ofSeconds(2))
            .build();
    return new RetryTemplate(policy);
  }

  private static boolean mayBeSentAgain(Throwable failure) {
    return MandrillHttp.neverReachedMandrill(failure)
        || mandrillAnsweredWithAnError(failure)
        || failedBeforeAnyAnswer(failure);
  }

  private static boolean mandrillAnsweredWithAnError(Throwable failure) {
    return failure instanceof MandrillApiException;
  }

  private static boolean failedBeforeAnyAnswer(Throwable failure) {
    return failure instanceof ResourceAccessException
        && MandrillHttp.causes(failure).noneMatch(HttpTimeoutException.class::isInstance);
  }
}
