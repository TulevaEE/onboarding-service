package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.digidoc4j.Configuration.Mode.TEST;

import org.digidoc4j.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DigiDocConfigurationTest {

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
