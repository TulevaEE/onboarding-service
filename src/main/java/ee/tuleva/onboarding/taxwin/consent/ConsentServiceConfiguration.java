package ee.tuleva.onboarding.taxwin.consent;

import static java.time.Duration.ofSeconds;

import ee.tuleva.onboarding.taxwin.TaxHeadroomProperties;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(TaxHeadroomProperties.class)
public class ConsentServiceConfiguration {

  @Bean
  public RestClient consentServiceRestClient(
      RestClient.Builder builder, TaxHeadroomProperties properties) {
    var httpClient = HttpClient.newBuilder().connectTimeout(ofSeconds(3)).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(ofSeconds(10));
    return builder
        .baseUrl(properties.xRoadUrl() + "/r1/" + properties.consentService())
        .defaultHeader("X-Road-Client", properties.clientId())
        .requestFactory(requestFactory)
        .build();
  }
}
