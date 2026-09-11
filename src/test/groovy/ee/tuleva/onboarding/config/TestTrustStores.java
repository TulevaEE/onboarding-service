package ee.tuleva.onboarding.config;

import ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture;
import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import lombok.SneakyThrows;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

/** Builds a real, on-disk trust store so config-package tests can load it like production does. */
final class TestTrustStores {

  static final ResourceLoader RESOURCE_LOADER = new DefaultResourceLoader();

  private TestTrustStores() {}

  @SneakyThrows
  static String writeTrustStoreContainingASelfSignedCertificate() {
    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    trustStore.load(null, null);
    trustStore.setCertificateEntry(
        "test-ca", WebEidCertificateFixture.certificateWithSubjectDn("CN=Test Trust Store CA"));

    File file = File.createTempFile("truststore", ".p12");
    file.deleteOnExit();
    // Loaded with a null password in production (MobileIdConfiguration#getTrustStore,
    // SmartIdClientConfiguration#trustStore), so it must be written with a null password too.
    try (var out = new FileOutputStream(file)) {
      trustStore.store(out, null);
    }
    return file.getAbsolutePath();
  }
}
