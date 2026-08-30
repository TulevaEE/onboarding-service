package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class MsciIndexConfiguration {

  static final String MSCI_ACWI_KEY = "MSCI_ACWI";
  static final String MSCI_WORLD_KEY = "MSCI_WORLD";
  static final String MSCI_EM_KEY = "MSCI_EM";

  @Bean
  MsciIndexRetriever msciAcwiIndexRetriever(RestClient.Builder restClientBuilder, Clock clock) {
    return new MsciIndexRetriever(MSCI_ACWI_KEY, "892400", restClientBuilder, clock);
  }

  @Bean
  MsciIndexRetriever msciWorldIndexRetriever(RestClient.Builder restClientBuilder, Clock clock) {
    return new MsciIndexRetriever(MSCI_WORLD_KEY, "990100", restClientBuilder, clock);
  }

  @Bean
  MsciIndexRetriever msciEmIndexRetriever(RestClient.Builder restClientBuilder, Clock clock) {
    return new MsciIndexRetriever(MSCI_EM_KEY, "891800", restClientBuilder, clock);
  }
}
