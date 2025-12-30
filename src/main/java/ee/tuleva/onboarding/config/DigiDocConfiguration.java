package ee.tuleva.onboarding.config;

import static org.digidoc4j.Configuration.Mode.PROD;
import static org.digidoc4j.Configuration.Mode.TEST;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class DigiDocConfiguration {

  // TODO: Remove after https://github.com/open-eid/test-TL/pull/26 is merged
  private static final String TEST_TSL_WITH_ESTEID2025 =
      "https://raw.githubusercontent.com/open-eid/test-TL/zetes/EE_T.xml";

  @Bean
  @Profile("production")
  public org.digidoc4j.Configuration digiDocConfigProd() {
    var configuration = new org.digidoc4j.Configuration(PROD);
    configuration.setTrustedTerritories("EE");
    return configuration;
  }

  @Bean
  @ConditionalOnMissingBean(org.digidoc4j.Configuration.class)
  public org.digidoc4j.Configuration digiDocConfigDev() {
    var configuration = new org.digidoc4j.Configuration(TEST);
    configuration.setTrustedTerritories("EE_T");
    configuration.setPreferAiaOcsp(false);
    configuration.setLotlLocation(TEST_TSL_WITH_ESTEID2025);
    return configuration;
  }
}
