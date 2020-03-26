package ee.tuleva.onboarding.config;

import com.codeborne.security.mobileid.MobileIDAuthenticator;
import ee.sk.mid.MidAuthenticationResponseValidator;
import ee.sk.mid.MidClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MobileIdConfiguration {

  @Value("${digidoc.service.url}")
  private String digidocServiceUrl;

  @Value("${truststore.path}")
  private String trustStorePath;

  @Value("${mobile-id.service.name}")
  private String serviceName;

  @Bean
  MobileIDAuthenticator mobileIDAuthenticator() {
    System.setProperty("javax.net.ssl.trustStore", trustStorePath);
    log.info("Setting global ssl truststore to {}", this.trustStorePath);
    log.info(
        "setting digidoc service url to {} with name {}", this.digidocServiceUrl, this.serviceName);
    return new MobileIDAuthenticator(digidocServiceUrl, serviceName);
  }

  @Bean
  MidClient mobileIDClient() {
    MidClient client =
        MidClient.newBuilder()
            .withHostUrl("https://tsp.demo.sk.ee/mid-api")
            .withRelyingPartyUUID("00000000-0000-0000-0000-000000000000")
            .withRelyingPartyName("DEMO")
            .build();
    return client;
  }

  @Bean
  MidAuthenticationResponseValidator mobileIDValidator() {
    return new MidAuthenticationResponseValidator();
  }
}
