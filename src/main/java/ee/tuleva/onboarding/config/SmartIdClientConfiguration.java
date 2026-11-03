package ee.tuleva.onboarding.config;

import static java.util.concurrent.TimeUnit.SECONDS;

import ee.sk.smartid.CertificateChoiceResponseValidator;
import ee.sk.smartid.CertificateValidator;
import ee.sk.smartid.CertificateValidatorImpl;
import ee.sk.smartid.DeviceLinkAuthenticationResponseValidator;
import ee.sk.smartid.NotificationAuthenticationResponseValidator;
import ee.sk.smartid.SignatureResponseValidator;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.TrustedCACertStore;
import ee.sk.smartid.exception.permanent.SmartIdClientException;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.tuleva.onboarding.auth.SmartIdProperties;
import ee.tuleva.onboarding.auth.ocsp.CertificateRevocationCheck;
import ee.tuleva.onboarding.auth.ocsp.OCSPUtils;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SmartIdProperties.class)
public class SmartIdClientConfiguration {

  @Value("${truststore.path}")
  private String trustStorePath;

  @Bean
  public SmartIdClient smartIdClient(SmartIdProperties properties, KeyStore trustStore) {
    SmartIdClient smartIdClient = new SmartIdClient();
    smartIdClient.setRelyingPartyUUID(properties.relyingPartyUUID());
    smartIdClient.setRelyingPartyName(properties.relyingPartyName());
    smartIdClient.setHostUrl(properties.rpApiUrl());
    smartIdClient.setSessionStatusResponseSocketOpenTime(SECONDS, 1L);
    smartIdClient.setTrustStore(trustStore);
    return smartIdClient;
  }

  @Bean
  public SmartIdConnector smartIdConnector(SmartIdClient smartIdClient) {
    return smartIdClient.getSmartIdConnector();
  }

  @Bean
  public TrustedCACertStore smartIdTrustedCaCertStore(
      SmartIdProperties properties, ResourceLoader resourceLoader) {
    return SmartIdTrustedCaCertificates.load(resourceLoader, properties.trustedCaCertificates());
  }

  @Bean
  public CertificateValidator smartIdCertificateValidator(
      TrustedCACertStore smartIdTrustedCaCertStore) {
    return new CertificateValidatorImpl(smartIdTrustedCaCertStore);
  }

  @Bean
  public RestClient ocspRestClient(RestClient.Builder restClientBuilder) {
    final Duration OCSP_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    final Duration OCSP_READ_TIMEOUT = Duration.ofSeconds(5);
    var requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(OCSP_CONNECT_TIMEOUT).build());
    requestFactory.setReadTimeout(OCSP_READ_TIMEOUT);
    return restClientBuilder.clone().requestFactory(requestFactory).build();
  }

  @Bean
  public RetryTemplate ocspRetryTemplate() {
    return OcspRetryTemplates.askingAgainAtOnce();
  }

  @Bean
  public CertificateRevocationCheck smartIdCertificateRevocationCheck(
      TrustedCACertStore smartIdTrustedCaCertStore,
      @Qualifier("ocspRestClient") RestClient ocspRestClient,
      OCSPUtils ocspUtils,
      Clock clock,
      @Qualifier("ocspRetryTemplate") RetryTemplate ocspRetryTemplate) {
    return new CertificateRevocationCheck(
        SmartIdIssuingCaCertificates.of(smartIdTrustedCaCertStore),
        ocspRestClient,
        ocspUtils,
        clock,
        ocspRetryTemplate);
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
