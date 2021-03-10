package ee.tuleva.onboarding.config;

import static org.digidoc4j.Configuration.Mode.PROD;
import static org.digidoc4j.Configuration.Mode.TEST;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class DigiDocConfiguration {

  @Bean
  @Profile("production")
  public org.digidoc4j.Configuration digiDocConfigProd() {
    return new org.digidoc4j.Configuration(PROD);
  }

  @Bean
  @ConditionalOnMissingBean(org.digidoc4j.Configuration.class)
  public org.digidoc4j.Configuration digiDocConfigDev() {
    // use PROD for testing signing
    return new org.digidoc4j.Configuration(TEST);
  }
}
