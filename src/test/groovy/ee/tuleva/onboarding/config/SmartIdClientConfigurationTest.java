package ee.tuleva.onboarding.config;

import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.demoProperties;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.demoTestAccountSigningCertificate;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.liveProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.sk.smartid.CertificateParser;
import ee.sk.smartid.CertificateValidator;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.exception.UnprocessableSmartIdResponseException;
import ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture;
import java.lang.reflect.Field;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

class SmartIdClientConfigurationTest {

  private final SmartIdClientConfiguration configuration = new SmartIdClientConfiguration();
  private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

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
  void smartIdClientAppliesThePropertiesTheSocketOpenTimeAndTheTlsTrustStore() throws Exception {
    KeyStore trustStore =
        trustStoreWithCertificates(WebEidCertificateFixture.certificateWithSubjectDn("CN=CA"));

    SmartIdClient smartIdClient = configuration.smartIdClient(demoProperties, trustStore);

    assertThat(smartIdClient.getRelyingPartyUUID()).isEqualTo(demoProperties.relyingPartyUUID());
    assertThat(smartIdClient.getRelyingPartyName()).isEqualTo("DEMO");
    assertThat(privateField(smartIdClient, "hostUrl"))
        .isEqualTo("https://sid.demo.sk.ee/smart-id-rp/v3/");
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
    SmartIdClient smartIdClient = configuration.smartIdClient(demoProperties, trustStore);

    var connector = configuration.smartIdConnector(smartIdClient);

    assertThat(connector).isSameAs(smartIdClient.getSmartIdConnector());
  }

  @Test
  void demoCertificateValidatorTrustsACertificateIssuedByTheDemoCa() {
    CertificateValidator validator =
        configuration.smartIdCertificateValidator(demoProperties, resourceLoader);

    assertThatCode(() -> validator.validate(demoTestAccountCertificate()))
        .doesNotThrowAnyException();
  }

  @Test
  void liveCertificateValidatorRejectsACertificateIssuedByTheDemoCa() {
    CertificateValidator validator =
        configuration.smartIdCertificateValidator(liveProperties, resourceLoader);

    assertThatThrownBy(() -> validator.validate(demoTestAccountCertificate()))
        .isInstanceOf(UnprocessableSmartIdResponseException.class);
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

  private static X509Certificate demoTestAccountCertificate() {
    return CertificateParser.parseX509Certificate(demoTestAccountSigningCertificate);
  }
}
