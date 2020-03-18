package ee.tuleva.onboarding.config;

import ee.sk.smartid.AuthenticationResponseValidator;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.exception.TechnicalErrorException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@Configuration
@RequiredArgsConstructor
public class SmartIdClientConfiguration {

  @Value("${truststore.path}")
  private String trustStorePath;

  @Bean
  @ConfigurationProperties(prefix = "smartid")
  public SmartIdClient smartIdClient() {
    return new SmartIdClient();
  }

  @Bean
  public Executor smartIdExecutor() {
    return Executors.newFixedThreadPool(100);
  }

  @Bean
  public AuthenticationResponseValidator authenticationResponseValidator(
      ResourceLoader resourceLoader) {
    AuthenticationResponseValidator validator = new AuthenticationResponseValidator();
    initializeTrustedCertificatesFromTrustStore(validator, resourceLoader);
    return validator;
  }

  private void initializeTrustedCertificatesFromTrustStore(
      AuthenticationResponseValidator validator, ResourceLoader resourceLoader) {
    try {
      Resource resource = resourceLoader.getResource("file:" + trustStorePath);
      InputStream inputStream = resource.getInputStream();
      KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(inputStream, null);
      Enumeration<String> aliases = trustStore.aliases();

      while (aliases.hasMoreElements()) {
        String alias = aliases.nextElement();
        X509Certificate certificate = (X509Certificate) trustStore.getCertificate(alias);
        validator.addTrustedCACertificate(certificate);
      }
    } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
      throw new TechnicalErrorException("Error initializing trusted CA certificates", e);
    }
  }
}
