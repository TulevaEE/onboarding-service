package ee.tuleva.onboarding.config.http;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfiguration {

    @Bean
    @Primary
    RestTemplate restTemplate() {
        final RestTemplate restTemplate = new RestTemplate();
        return restTemplate;
    }

}
