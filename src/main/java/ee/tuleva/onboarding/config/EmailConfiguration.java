package ee.tuleva.onboarding.config;

import io.github.erkoristhein.mailchimp.ApiClient;
import io.github.erkoristhein.mailchimp.api.MessagesApi;
import io.github.erkoristhein.mailchimp.marketing.api.CampaignsApi;
import io.github.erkoristhein.mailchimp.marketing.api.ListsApi;
import io.github.erkoristhein.mailchimp.marketing.api.ReportsApi;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmailConfiguration {

  @Value("${mandrill.url}")
  private String mandrillUrl;

  @Value("${mailchimp.url}")
  private String mailchimpUrl;

  @Value("${mailchimp.key:#{null}}")
  private @Nullable String mailchimpKey;

  @Bean
  public ApiClient mailchimpTransactionalApiClient() {
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
  public MessagesApi mailchimpTransactionalMessagesApi(ApiClient mailchimpTransactionalApiClient) {
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
