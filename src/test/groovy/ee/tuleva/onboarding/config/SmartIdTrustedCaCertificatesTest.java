package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.sk.smartid.TrustedCACertStore;
import ee.sk.smartid.exception.permanent.SmartIdClientException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class SmartIdTrustedCaCertificatesTest {

  private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

  @Test
  void loadsTheDemoRootsAsAnchorsAndTheDemoIssuingCasAsIntermediates() {
    TrustedCACertStore store =
        SmartIdTrustedCaCertificates.load(resourceLoader, "classpath:smart-id/demo/*.pem");

    assertThat(anchorNames(store))
        .containsExactlyInAnyOrder(
            "TEST of SK ID Solutions ROOT G1E",
            "TEST of SK ID Solutions ROOT G1R",
            "TEST of EE Certification Centre Root CA");
    assertThat(intermediateNames(store))
        .containsExactlyInAnyOrder(
            "TEST of SK ID Solutions EID-Q 2024E",
            "TEST of SK ID Solutions EID-Q 2024R",
            "TEST of EID-SK 2016");
    assertThat(store.isOcspEnabled()).isFalse();
  }

  @Test
  void loadsTheLiveRootsAsAnchorsAndTheLiveIssuingCasAsIntermediates() {
    TrustedCACertStore store =
        SmartIdTrustedCaCertificates.load(resourceLoader, "classpath:smart-id/live/*.pem");

    assertThat(anchorNames(store))
        .containsExactlyInAnyOrder(
            "SK ID Solutions ROOT G1E",
            "SK ID Solutions ROOT G1R",
            "EE Certification Centre Root CA");
    assertThat(intermediateNames(store))
        .containsExactlyInAnyOrder(
            "SK ID Solutions EID-Q 2024E", "SK ID Solutions EID-Q 2024R", "EID-SK 2016");
  }

  @Test
  void failsWhenNoCertificatesMatch() {
    assertThatThrownBy(
            () ->
                SmartIdTrustedCaCertificates.load(
                    resourceLoader, "classpath:smart-id/nonexistent/*.pem"))
        .isInstanceOf(SmartIdClientException.class);
  }

  @Test
  void failsWhenAnIssuingCaHasNoRootToChainTo() {
    assertThatThrownBy(
            () ->
                SmartIdTrustedCaCertificates.load(
                    resourceLoader, "classpath:smart-id/live/*EID-Q_2024E.pem"))
        .isInstanceOf(SmartIdClientException.class);
  }

  private static List<String> anchorNames(TrustedCACertStore store) {
    return store.getTrustAnchors().stream()
        .map(TrustAnchor::getTrustedCert)
        .map(SmartIdTrustedCaCertificatesTest::commonName)
        .toList();
  }

  private static List<String> intermediateNames(TrustedCACertStore store) {
    return store.getTrustedCACertificates().stream()
        .map(SmartIdTrustedCaCertificatesTest::commonName)
        .toList();
  }

  private static String commonName(X509Certificate certificate) {
    String subject = certificate.getSubjectX500Principal().getName();
    return java.util.Arrays.stream(subject.split(","))
        .map(String::trim)
        .filter(part -> part.startsWith("CN="))
        .map(part -> part.substring(3))
        .findFirst()
        .orElseThrow();
  }
}
