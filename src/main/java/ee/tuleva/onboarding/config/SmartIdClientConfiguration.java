package ee.tuleva.onboarding.config;

import static java.util.concurrent.TimeUnit.SECONDS;

import ee.sk.smartid.CertificateChoiceResponseValidator;
import ee.sk.smartid.CertificateValidator;
import ee.sk.smartid.CertificateValidatorImpl;
import ee.sk.smartid.DeviceLinkAuthenticationResponseValidator;
import ee.sk.smartid.NotificationAuthenticationResponseValidator;
import ee.sk.smartid.SignatureResponseValidator;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.exception.permanent.SmartIdClientException;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.tuleva.onboarding.auth.SmartIdProperties;
import ee.tuleva.onboarding.auth.ocsp.OCSPUtils;
import ee.tuleva.onboarding.auth.smartid.SmartIdCertificateRevocationCheck;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SmartIdProperties.class)
@RequiredArgsConstructor
public class SmartIdClientConfiguration {

  @Value("${truststore.path}")
  private String trustStorePath;

  @Bean
  public SmartIdClient smartIdClient(SmartIdProperties properties, KeyStore trustStore) {
    SmartIdClient smartIdClient = new SmartIdClient();
    smartIdClient.setRelyingPartyUUID(properties.relyingPartyUUID());
    smartIdClient.setRelyingPartyName(properties.relyingPartyName());
    smartIdClient.setHostUrl(properties.hostUrl());
    smartIdClient.setSessionStatusResponseSocketOpenTime(SECONDS, 1L);
    smartIdClient.setTrustStore(trustStore);
    return smartIdClient;
  }

  @Bean
  public SmartIdConnector smartIdConnector(SmartIdClient smartIdClient) {
    return smartIdClient.getSmartIdConnector();
  }

  @Bean
  public CertificateValidator smartIdCertificateValidator(
      SmartIdProperties properties, ResourceLoader resourceLoader) {
    return new CertificateValidatorImpl(
        SmartIdTrustedCaCertificates.load(resourceLoader, properties.trustedCaCertificates()));
  }

  @Bean
  public SmartIdCertificateRevocationCheck smartIdCertificateRevocationCheck(
      SmartIdProperties properties,
      ResourceLoader resourceLoader,
      RestClient.Builder restClientBuilder,
      OCSPUtils ocspUtils,
      Clock clock) {
    var requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    requestFactory.setReadTimeout(Duration.ofSeconds(5));
    return new SmartIdCertificateRevocationCheck(
        SmartIdTrustedCaCertificates.read(resourceLoader, properties.trustedCaCertificates()),
        restClientBuilder.clone().requestFactory(requestFactory).build(),
        ocspUtils,
        clock);
  }

  @Bean
  public DeviceLinkAuthenticationResponseValidator deviceLinkAuthenticationResponseValidator(
      CertificateValidator smartIdCertificateValidator) {
    return DeviceLinkAuthenticationResponseValidator.defaultSetupWithCertificateValidator(
        smartIdCertificateValidator);
  }

  @Bean
  public NotificationAuthenticationResponseValidator notificationAuthenticationResponseValidator(
      CertificateValidator smartIdCertificateValidator) {
    return NotificationAuthenticationResponseValidator.defaultSetupWithCertificateValidator(
        smartIdCertificateValidator);
  }

  @Bean
  public CertificateChoiceResponseValidator certificateChoiceResponseValidator(
      CertificateValidator smartIdCertificateValidator) {
    return new CertificateChoiceResponseValidator(smartIdCertificateValidator);
  }

  @Bean
  public SignatureResponseValidator signatureResponseValidator(
      CertificateValidator smartIdCertificateValidator) {
    return new SignatureResponseValidator(smartIdCertificateValidator);
  }

  @Bean
  public KeyStore trustStore(ResourceLoader resourceLoader) {
    try {
      Resource resource = resourceLoader.getResource("file:" + trustStorePath);
      InputStream inputStream = resource.getInputStream();
      KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(inputStream, null);
      return trustStore;
    } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
      throw new SmartIdClientException("Error initializing trusted CA certificates", e);
    }
  }
}
