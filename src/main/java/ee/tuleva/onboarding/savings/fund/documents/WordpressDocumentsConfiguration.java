package ee.tuleva.onboarding.savings.fund.documents;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@Configuration
@ConfigurationProperties(prefix = "wordpress-documents")
@Getter
@Setter
public class WordpressDocumentsConfiguration {

  private String url = "https://tuleva.ee/wp-json/wp/v2";
  private String slug = "tuleva-taiendav-kogumisfond-dokumendid";

  @Bean
  RetryTemplate wordpressDocumentsRetryTemplate() {
    return new RetryTemplate(
        RetryPolicy.builder()
            .includes(HttpServerErrorException.class, ResourceAccessException.class)
            .excludes(HttpClientErrorException.class)
            .maxRetries(3)
            .delay(Duration.ofSeconds(1))
            .multiplier(2)
            .maxDelay(Duration.ofSeconds(10))
            .build());
  }
}
