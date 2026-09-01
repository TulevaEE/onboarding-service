package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;

import ee.sk.smartid.AuthenticationResponseValidator;
import ee.sk.smartid.SmartIdClient;
import ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture;
import java.lang.reflect.Field;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SmartIdClientConfigurationTest {

  private final SmartIdClientConfiguration configuration = new SmartIdClientConfiguration();

  private static KeyStore trustStoreWithCertificates(X509Certificate... certificates)
      throws Exception {
    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    trustStore.load(null, null);
    for (int i = 0; i < certificates.length; i++) {
      trustStore.setCertificateEntry("ca-" + i, certificates[i]);
    }
    return trustStore;
  }

  private static Object privateField(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  @Test
  void smartIdClientAppliesTheSocketOpenTimeAndTheTrustStore() throws Exception {
    KeyStore trustStore =
        trustStoreWithCertificates(WebEidCertificateFixture.certificateWithSubjectDn("CN=CA"));

    SmartIdClient smartIdClient = configuration.smartIdClient(trustStore);

    assertThat(smartIdClient).isNotNull();
    assertThat(privateField(smartIdClient, "sessionStatusResponseSocketOpenTimeUnit"))
        .isEqualTo(TimeUnit.SECONDS);
    assertThat(privateField(smartIdClient, "sessionStatusResponseSocketOpenTimeValue"))
        .isEqualTo(1L);
    assertThat(privateField(smartIdClient, "trustSslContext")).isNotNull();
  }

  @Test
  void smartIdConnectorComesFromTheConfiguredClient() throws Exception {
    KeyStore trustStore =
        trustStoreWithCertificates(WebEidCertificateFixture.certificateWithSubjectDn("CN=CA"));
    SmartIdClient smartIdClient = configuration.smartIdClient(trustStore);

    var connector = configuration.smartIdConnector(smartIdClient);

    assertThat(connector).isSameAs(smartIdClient.getSmartIdConnector());
  }

  @Test
  void authenticationResponseValidatorTrustsEveryCertificateInTheTrustStore() throws Exception {
    // AuthenticationResponseValidator() always seeds a bundled default trust list, so we assert
    // that our two certificates were added on top of it rather than that the result is exact.
    X509Certificate first = WebEidCertificateFixture.certificateWithSubjectDn("CN=First CA");
    X509Certificate second = WebEidCertificateFixture.certificateWithSubjectDn("CN=Second CA");
    KeyStore trustStore = trustStoreWithCertificates(first, second);

    AuthenticationResponseValidator validator =
        configuration.authenticationResponseValidator(trustStore);

    assertThat(validator.getTrustedCACertificates()).contains(first, second);
  }

  @Test
  void authenticationResponseValidatorAddsNoneWhenTheTrustStoreIsEmpty() throws Exception {
    KeyStore trustStore = trustStoreWithCertificates();
    int defaultTrustedCertificateCount =
        new AuthenticationResponseValidator().getTrustedCACertificates().size();

    AuthenticationResponseValidator validator =
        configuration.authenticationResponseValidator(trustStore);

    assertThat(validator.getTrustedCACertificates()).hasSize(defaultTrustedCertificateCount);
  }

  @Test
  void trustStoreLoadsTheConfiguredFileFromDisk() throws Exception {
    ReflectionTestUtils.setField(
        configuration,
        "trustStorePath",
        TestTrustStores.writeTrustStoreContainingASelfSignedCertificate());

    KeyStore trustStore = configuration.trustStore(TestTrustStores.RESOURCE_LOADER);

    assertThat(trustStore).isNotNull();
    assertThat(java.util.Collections.list(trustStore.aliases())).hasSize(1);
  }
}
