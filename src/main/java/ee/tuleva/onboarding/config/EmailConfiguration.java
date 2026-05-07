package ee.tuleva.onboarding.config;

import com.microtripit.mandrillapp.lutung.MandrillApi;
import com.microtripit.mandrillapp.lutung.model.MandrillApiError;
import io.github.erkoristhein.mailchimp.ApiClient;
import io.github.erkoristhein.mailchimp.api.MessagesApi;
import io.github.erkoristhein.mailchimp.marketing.api.CampaignsApi;
import io.github.erkoristhein.mailchimp.marketing.api.ListsApi;
import io.github.erkoristhein.mailchimp.marketing.api.ReportsApi;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

@Configuration
@Slf4j
@Getter
public class EmailConfiguration {

  @Value("${mandrill.url}")
  private String mandrillUrl;

  @Value("${mandrill.key:#{null}}")
  private String mandrillKey;

  @Value("${mailchimp.url}")
  private String mailchimpUrl;

  @Value("${mailchimp.key:#{null}}")
  private String mailchimpKey;

  private Optional<String> mandrillKey() {
    return Optional.ofNullable(mandrillKey);
  }

  @Bean
  public MandrillApi mandrillApi() {
    if (mandrillKey().isEmpty()) {
      log.warn("Mandrill key not present.");
    }

    return mandrillKey().map(MandrillApi::new).orElse(null);
  }

  @Bean
  RetryTemplate emailServiceRetryTemplate() {
    var policy =
        RetryPolicy.builder()
            .includes(MandrillApiError.class, IOException.class)
            .maxRetries(2)
            .delay(Duration.ofMillis(200))
            .multiplier(3)
            .maxDelay(Duration.ofSeconds(2))
            .build();
    return new RetryTemplate(policy);
  }

  @Bean
  public io.github.erkoristhein.mailchimp.ApiClient mailchimpTransactionalApiClient() {
    ApiClient apiClient = new ApiClient().setBasePath(mandrillUrl);
    apiClient.setDebugging(true);
    return apiClient;
  }

  @Bean
  public io.github.erkoristhein.mailchimp.marketing.ApiClient mailchimpMarketingApiClient() {
    var apiClient = new io.github.erkoristhein.mailchimp.marketing.ApiClient();
    apiClient.setBasePath(mailchimpUrl);
    apiClient.setUsername("any");
    apiClient.setPassword(mailchimpKey);
    apiClient.setDebugging(true);
    return apiClient;
  }

  @Bean
  public MessagesApi mailchimpTransactionalMessagesApi(
      io.github.erkoristhein.mailchimp.ApiClient mailchimpTransactionalApiClient) {
    return new MessagesApi(mailchimpTransactionalApiClient);
  }

  @Bean
  public ListsApi mailchimpMarketingListsApi(
      io.github.erkoristhein.mailchimp.marketing.ApiClient mailchimpMarketingApiClient) {
    return new ListsApi(mailchimpMarketingApiClient);
  }

  @Bean
  public CampaignsApi mailchimpMarketingCampaignsApi(
      io.github.erkoristhein.mailchimp.marketing.ApiClient mailchimpMarketingApiClient) {
    return new CampaignsApi(mailchimpMarketingApiClient);
  }

  @Bean
  public ReportsApi mailchimpMarketingReportsApi(
      io.github.erkoristhein.mailchimp.marketing.ApiClient mailchimpMarketingApiClient) {
    return new ReportsApi(mailchimpMarketingApiClient);
  }
}
