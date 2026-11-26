package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erkoristhein.mailchimp.marketing.auth.HttpBasicAuth;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EmailConfigurationTest {

  private static final String MANDRILL_URL = "https://mandrillapp.com/api/1.0";
  private static final String MAILCHIMP_URL = "https://us1.api.mailchimp.com/3.0";
  private static final String MAILCHIMP_KEY = "test-mailchimp-key";

  private final EmailConfiguration configuration = new EmailConfiguration();

  private EmailConfiguration configured() {
    ReflectionTestUtils.setField(configuration, "mandrillUrl", MANDRILL_URL);
    ReflectionTestUtils.setField(configuration, "mailchimpUrl", MAILCHIMP_URL);
    ReflectionTestUtils.setField(configuration, "mailchimpKey", MAILCHIMP_KEY);
    return configuration;
  }

  @Test
  void mailchimpTransactionalApiClientPointsAtTheConfiguredMandrillUrlWithDebuggingOn() {
    configured();

    var apiClient = configuration.mailchimpTransactionalApiClient();

    assertThat(apiClient.getBasePath()).isEqualTo(MANDRILL_URL);
    assertThat(apiClient.isDebugging()).isTrue();
  }

  @Test
  void mailchimpMarketingApiClientPointsAtTheConfiguredMailchimpUrlWithBasicAuthAndDebugging() {
    configured();

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
    configured();
    var apiClient = configuration.mailchimpTransactionalApiClient();

    var messagesApi = configuration.mailchimpTransactionalMessagesApi(apiClient);

    assertThat(messagesApi.getApiClient()).isSameAs(apiClient);
  }

  @Test
  void mailchimpMarketingListsApiUsesTheProvidedApiClient() {
    configured();
    var apiClient = configuration.mailchimpMarketingApiClient();

    var listsApi = configuration.mailchimpMarketingListsApi(apiClient);

    assertThat(listsApi.getApiClient()).isSameAs(apiClient);
  }

  @Test
  void mailchimpMarketingCampaignsApiUsesTheProvidedApiClient() {
    configured();
    var apiClient = configuration.mailchimpMarketingApiClient();

    var campaignsApi = configuration.mailchimpMarketingCampaignsApi(apiClient);

    assertThat(campaignsApi.getApiClient()).isSameAs(apiClient);
  }

  @Test
  void mailchimpMarketingReportsApiUsesTheProvidedApiClient() {
    configured();
    var apiClient = configuration.mailchimpMarketingApiClient();

    var reportsApi = configuration.mailchimpMarketingReportsApi(apiClient);

    assertThat(reportsApi.getApiClient()).isSameAs(apiClient);
  }
}
