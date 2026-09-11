package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.digidoc4j.Configuration.Mode.TEST;

import org.digidoc4j.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DigiDocConfigurationTest {

  private final DigiDocConfiguration digiDocConfiguration = new DigiDocConfiguration();

  @Test
  void digiDocConfigProdUsesProductionModeAndTrustsOnlyEstonia() {
    Configuration configuration = digiDocConfiguration.digiDocConfigProd();

    assertThat(configuration).isNotNull();
    assertThat(configuration.isTest()).isFalse();
    assertThat(configuration.getTrustedTerritories()).containsExactly("EE");
  }

  @Test
  void digiDocConfigDevUsesTestModeAndTrustsEstonianTestTerritory() {
    Configuration configuration = digiDocConfiguration.digiDocConfigDev();

    assertThat(configuration).isNotNull();
    assertThat(configuration.isTest()).isTrue();
    assertThat(configuration.getTrustedTerritories()).containsExactly("EE_T");
  }

  @Test
  @DisplayName("Default test TSL contains Test ESTEID2025 certificate")
  void defaultTestTslContainsTestEsteid2025() {
    var configuration = new Configuration(TEST);
    configuration.setTrustedTerritories("EE_T");

    var tsl = configuration.getTSL();
    tsl.refresh();

    var certificates = tsl.getCertificates();
    System.out.println("Loaded " + certificates.size() + " certificates from default test TSL");

    var hasTestEsteid2025 =
        certificates.stream()
            .anyMatch(
                cert ->
                    cert.getCertificate()
                        .getSubjectX500Principal()
                        .getName()
                        .contains("Test ESTEID2025"));

    assertThat(hasTestEsteid2025).as("TSL should contain Test ESTEID2025 certificate").isTrue();
  }
}
