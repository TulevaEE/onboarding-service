package ee.tuleva.onboarding.auth.webeid;

import eu.webeid.security.challenge.ChallengeNonceGenerator;
import eu.webeid.security.challenge.ChallengeNonceGeneratorBuilder;
import eu.webeid.security.challenge.ChallengeNonceStore;
import eu.webeid.security.exceptions.JceException;
import eu.webeid.security.validator.AuthTokenValidator;
import eu.webeid.security.validator.AuthTokenValidatorBuilder;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collections;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebEidConfiguration {

  @Value("${web-eid.origin}")
  private String origin;

  @Value("${web-eid.nonce-ttl-minutes}")
  private int nonceTtlMinutes;

  @Bean
  public ChallengeNonceStore challengeNonceStore(ObjectFactory<HttpSession> httpSessionFactory) {
    return new SessionBackedChallengeNonceStore(httpSessionFactory);
  }

  @Bean
  public ChallengeNonceGenerator challengeNonceGenerator(ChallengeNonceStore challengeNonceStore) {
    return new ChallengeNonceGeneratorBuilder()
        .withNonceTtl(Duration.ofMinutes(nonceTtlMinutes))
        .withChallengeNonceStore(challengeNonceStore)
        .build();
  }

  @Bean
  public AuthTokenValidator authTokenValidator(KeyStore trustStore)
      throws KeyStoreException, JceException {
    return new AuthTokenValidatorBuilder()
        .withSiteOrigin(URI.create(origin))
        .withTrustedCertificateAuthorities(loadCertificatesFromTrustStore(trustStore))
        .build();
  }

  private X509Certificate[] loadCertificatesFromTrustStore(KeyStore trustStore)
      throws KeyStoreException {
    return Collections.list(trustStore.aliases()).stream()
        .map(alias -> getCertificate(trustStore, alias))
        .toArray(X509Certificate[]::new);
  }

  private X509Certificate getCertificate(KeyStore trustStore, String alias) {
    try {
      return (X509Certificate) trustStore.getCertificate(alias);
    } catch (KeyStoreException e) {
      throw new IllegalStateException("Failed to load certificate: alias=" + alias, e);
    }
  }
}
