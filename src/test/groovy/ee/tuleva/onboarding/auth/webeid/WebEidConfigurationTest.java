package ee.tuleva.onboarding.auth.webeid;

import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.ESTONIAN_CITIZEN_ID_CARD;
import static org.assertj.core.api.Assertions.assertThat;

import eu.webeid.security.challenge.ChallengeNonceGenerator;
import eu.webeid.security.challenge.ChallengeNonceStore;
import eu.webeid.security.validator.AuthTokenValidator;
import jakarta.servlet.http.HttpSession;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WebEidConfigurationTest {

  private final WebEidConfiguration configuration = new WebEidConfiguration();

  @Mock private ObjectFactory<HttpSession> httpSessionFactory;

  @Test
  void createsASessionBackedChallengeNonceStore() {
    ChallengeNonceStore store = configuration.challengeNonceStore(httpSessionFactory);

    assertThat(store).isInstanceOf(SessionBackedChallengeNonceStore.class);
  }

  @Test
  void createsAChallengeNonceGeneratorBackedByTheGivenStore() {
    ReflectionTestUtils.setField(configuration, "nonceTtlMinutes", 5);
    ChallengeNonceStore store = configuration.challengeNonceStore(httpSessionFactory);

    ChallengeNonceGenerator generator = configuration.challengeNonceGenerator(store);

    assertThat(generator).isNotNull();
  }

  @Test
  void createsAnAuthTokenValidatorTrustingEveryCertificateInTheTrustStore() throws Exception {
    ReflectionTestUtils.setField(configuration, "origin", "https://tuleva.ee");
    KeyStore trustStore = trustStoreContaining(sampleCertificate());

    AuthTokenValidator validator = configuration.authTokenValidator(trustStore);

    assertThat(validator).isNotNull();
  }

  @Test
  void loadsEveryCertificateFromTheTrustStoreByAlias() throws Exception {
    X509Certificate certificate = sampleCertificate();
    KeyStore trustStore = trustStoreContaining(certificate);

    X509Certificate[] loadedCertificates =
        ReflectionTestUtils.invokeMethod(
            configuration, "loadCertificatesFromTrustStore", trustStore);

    assertThat(loadedCertificates).containsExactly(certificate);
  }

  private static X509Certificate sampleCertificate() {
    return WebEidCertificateFixture.certificate(
        "Aadu", "Kadakas", "38888888888", ESTONIAN_CITIZEN_ID_CARD);
  }

  private static KeyStore trustStoreContaining(X509Certificate certificate) throws Exception {
    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    trustStore.load(null, null);
    trustStore.setCertificateEntry("test-ca", certificate);
    return trustStore;
  }
}
