package ee.tuleva.onboarding.config;

import ee.tuleva.onboarding.error.RestResponseErrorHandler;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2RestOperations;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;

@Configuration
public class Oauth2RestTemplateConfiguration {

    @Bean
    @ConfigurationProperties("epis.service.client")
    ClientCredentialsResourceDetails oauth2ClientCredentialsResourceDetails() {
        return new ClientCredentialsResourceDetails();
    }

    @Bean
    public OAuth2RestOperations oauth2RestTemplate(ClientCredentialsResourceDetails resourceDetails, RestResponseErrorHandler errorHandler) {
        OAuth2RestTemplate template = new OAuth2RestTemplate(resourceDetails);
        template.setErrorHandler(errorHandler);
        return template;
    }
}
