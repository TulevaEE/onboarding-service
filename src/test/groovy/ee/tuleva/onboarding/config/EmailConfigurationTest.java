package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.microtripit.mandrillapp.lutung.MandrillApi;
import com.microtripit.mandrillapp.lutung.model.MandrillApiError;
import io.github.erkoristhein.mailchimp.marketing.auth.HttpBasicAuth;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class EmailConfigurationTest {

  private static final String MANDRILL_URL = "https://mandrillapp.com/api/1.0";
  private static final String MAILCHIMP_URL = "https://us1.api.mailchimp.com/3.0";
  private static final String MAILCHIMP_KEY = "test-mailchimp-key";

  private final EmailConfiguration configuration = new EmailConfiguration();

  private ListAppender<ILoggingEvent> logAppender;
  private Logger logger;

  @BeforeEach
  void attachLogAppender() {
    logger = (Logger) LoggerFactory.getLogger(EmailConfiguration.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    logger.addAppender(logAppender);
  }

  @AfterEach
  void detachLogAppender() {
    logger.detachAppender(logAppender);
  }

  private EmailConfiguration configWithMandrillKey(String mandrillKey) {
    ReflectionTestUtils.setField(configuration, "mandrillUrl", MANDRILL_URL);
    ReflectionTestUtils.setField(configuration, "mandrillKey", mandrillKey);
    ReflectionTestUtils.setField(configuration, "mailchimpUrl", MAILCHIMP_URL);
    ReflectionTestUtils.setField(configuration, "mailchimpKey", MAILCHIMP_KEY);
    return configuration;
  }

  @Test
  void mandrillApiIsPresentWhenTheKeyIsConfigured() {
    configWithMandrillKey("test-mandrill-key");

    MandrillApi mandrillApi = configuration.mandrillApi();

    assertThat(mandrillApi).isNotNull();
    assertThat(logAppender.list).isEmpty();
  }

  @Test
  void mandrillApiIsAbsentWhenTheKeyIsMissing() {
    configWithMandrillKey(null);

    MandrillApi mandrillApi = configuration.mandrillApi();

    assertThat(mandrillApi).isNull();
    assertThat(logAppender.list).hasSize(1);
    assertThat(logAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
  }

  @Test
  void emailServiceRetryTemplateRetriesOnMandrillErrorsAndIoExceptions() throws RetryException {
    RetryTemplate retryTemplate = configuration.emailServiceRetryTemplate();
    var attempts = new java.util.concurrent.atomic.AtomicInteger();

    String result =
        retryTemplate.execute(
            () -> {
              if (attempts.incrementAndGet() < 2) {
                throw new IOException("simulated failure");
              }
              return "ok";
            });

    assertThat(result).isEqualTo("ok");
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void emailServiceRetryTemplateRetriesOnMandrillApiErrors() throws RetryException {
    RetryTemplate retryTemplate = configuration.emailServiceRetryTemplate();
    var attempts = new java.util.concurrent.atomic.AtomicInteger();

    String result =
        retryTemplate.execute(
            () -> {
              if (attempts.incrementAndGet() < 2) {
                throw new MandrillApiError("simulated mandrill failure");
              }
              return "ok";
            });

    assertThat(result).isEqualTo("ok");
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void emailServiceRetryTemplateDoesNotRetryUnlistedExceptions() {
    RetryTemplate retryTemplate = configuration.emailServiceRetryTemplate();
    var attempts = new java.util.concurrent.atomic.AtomicInteger();

    assertThatThrownBy(
            () ->
                retryTemplate.invoke(
                    () -> {
                      attempts.incrementAndGet();
                      throw new IllegalStateException("not retryable");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(attempts.get()).isEqualTo(1);
  }

  @Test
  void mailchimpTransactionalApiClientPointsAtTheConfiguredMandrillUrlWithDebuggingOn() {
    configWithMandrillKey("test-mandrill-key");

    var apiClient = configuration.mailchimpTransactionalApiClient();

    assertThat(apiClient.getBasePath()).isEqualTo(MANDRILL_URL);
    assertThat(apiClient.isDebugging()).isTrue();
  }

  @Test
  void mailchimpMarketingApiClientPointsAtTheConfiguredMailchimpUrlWithBasicAuthAndDebugging() {
    configWithMandrillKey("test-mandrill-key");

    var apiClient = configuration.mailchimpMarketingApiClient();

    assertThat(apiClient.getBasePath()).isEqualTo(MAILCHIMP_URL);
    assertThat(apiClient.isDebugging()).isTrue();
    var basicAuth =
        apiClient.getAuthentications().values().stream()
            .filter(HttpBasicAuth.class::isInstance)
            .map(HttpBasicAuth.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(basicAuth.getUsername()).isEqualTo("any");
    assertThat(basicAuth.getPassword()).isEqualTo(MAILCHIMP_KEY);
  }

  @Test
  void mailchimpTransactionalMessagesApiUsesTheProvidedApiClient() {
    configWithMandrillKey("test-mandrill-key");
    var apiClient = configuration.mailchimpTransactionalApiClient();

    var messagesApi = configuration.mailchimpTransactionalMessagesApi(apiClient);

    assertThat(messagesApi.getApiClient()).isSameAs(apiClient);
  }

  @Test
  void mailchimpMarketingListsApiUsesTheProvidedApiClient() {
    configWithMandrillKey("test-mandrill-key");
    var apiClient = configuration.mailchimpMarketingApiClient();

    var listsApi = configuration.mailchimpMarketingListsApi(apiClient);

    assertThat(listsApi.getApiClient()).isSameAs(apiClient);
  }

  @Test
  void mailchimpMarketingCampaignsApiUsesTheProvidedApiClient() {
    configWithMandrillKey("test-mandrill-key");
    var apiClient = configuration.mailchimpMarketingApiClient();

    var campaignsApi = configuration.mailchimpMarketingCampaignsApi(apiClient);

    assertThat(campaignsApi.getApiClient()).isSameAs(apiClient);
  }

  @Test
  void mailchimpMarketingReportsApiUsesTheProvidedApiClient() {
    configWithMandrillKey("test-mandrill-key");
    var apiClient = configuration.mailchimpMarketingApiClient();

    var reportsApi = configuration.mailchimpMarketingReportsApi(apiClient);

    assertThat(reportsApi.getApiClient()).isSameAs(apiClient);
  }
}
