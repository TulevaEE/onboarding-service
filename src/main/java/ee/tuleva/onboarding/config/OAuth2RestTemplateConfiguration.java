package ee.tuleva.onboarding.config;

import ee.tuleva.onboarding.auth.http.AuthorizedClientManagerOAuth2Interceptor;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestTemplate;

@Configuration
@RequiredArgsConstructor
public class OAuth2RestTemplateConfiguration {

  public static final String CLIENT_CREDENTIALS_REST_TEMPLATE = "clientCredentialsRestTemplate";
  public static final String USER_TOKEN_REST_TEMPLATE = "userTokenRestTemplate";

  private final RestTemplateBuilder restTemplateBuilder;
  private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
  private final ClientRegistrationRepository clientRegistrationRepository;

  @Bean(CLIENT_CREDENTIALS_REST_TEMPLATE)
  public RestTemplate clientCredentialsRestTemplate() {
    var clientRegistration =
        clientRegistrationRepository.findByRegistrationId("onboarding-service");

    return restTemplateBuilder
        .additionalInterceptors(
            new AuthorizedClientManagerOAuth2Interceptor(
                authorizedClientManager(), clientRegistration))
        .setConnectTimeout(Duration.ofSeconds(60))
        .setReadTimeout(Duration.ofSeconds(60))
        .build();
  }

  @Bean(USER_TOKEN_REST_TEMPLATE)
  public RestTemplate userTokenRestTemplate() {
    var clientRegistration = clientRegistrationRepository.findByRegistrationId("onboarding-client");

    return restTemplateBuilder
        .additionalInterceptors(
            new AuthorizedClientManagerOAuth2Interceptor(
                authorizedClientManager(), clientRegistration))
        .setConnectTimeout(Duration.ofSeconds(60))
        .setReadTimeout(Duration.ofSeconds(60))
        .build();
  }

  @Bean
  OAuth2AuthorizedClientManager authorizedClientManager() {
    var authorizedClientProvider =
        OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials()
            .authorizationCode()
            .build();

    var authorizedClientManager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            clientRegistrationRepository, oAuth2AuthorizedClientService);
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

    return authorizedClientManager;
  }
}
