package ee.tuleva.onboarding.config;

import static org.digidoc4j.Configuration.Mode.PROD;
import static org.digidoc4j.Configuration.Mode.TEST;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class DigiDocConfiguration {

  @Bean
  @Profile("production")
  public org.digidoc4j.Configuration digiDocConfigProd(Clock clock) {
    var configuration = new org.digidoc4j.Configuration(PROD);
    configuration.setTrustedTerritories("EE");
    return keepingTheLastValidTrustList(configuration, clock);
  }

  @Bean
  @ConditionalOnMissingBean(org.digidoc4j.Configuration.class)
  public org.digidoc4j.Configuration digiDocConfigDev(Clock clock) {
    var configuration = new org.digidoc4j.Configuration(TEST);
    configuration.setTrustedTerritories("EE_T");
    return keepingTheLastValidTrustList(configuration, clock);
  }

  private static org.digidoc4j.Configuration keepingTheLastValidTrustList(
      org.digidoc4j.Configuration configuration, Clock clock) {
    configuration.setTslRefreshCallback(
        new LastValidTrustListRefreshCallback(configuration, clock));
    return configuration;
  }
}
