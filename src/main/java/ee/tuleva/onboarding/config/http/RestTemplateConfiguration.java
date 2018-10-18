package ee.tuleva.onboarding.config.http;

import ee.tuleva.onboarding.error.RestResponseErrorHandler;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfiguration {

    @Bean
    @Primary
    RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder, RestResponseErrorHandler errorHandler) {
        return restTemplateBuilder
            .errorHandler(errorHandler)
            .setConnectTimeout(60_000)
            .setReadTimeout(60_000)
            .build();
    }

}
