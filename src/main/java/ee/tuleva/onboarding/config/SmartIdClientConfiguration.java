package ee.tuleva.onboarding.config;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

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
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Configuration
@EnableConfigurationProperties(SmartIdProperties.class)
@RequiredArgsConstructor
@Slf4j
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
  public RestClient smartIdOcspRestClient(RestClient.Builder restClientBuilder) {
    var requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    requestFactory.setReadTimeout(Duration.ofSeconds(5));
    return restClientBuilder.clone().requestFactory(requestFactory).build();
  }

  @Bean
  public RetryTemplate smartIdOcspRetryTemplate() {
    final int RETRIES_WHILE_THE_PERSON_WAITS = 2;
    final Duration DELAY_BEFORE_ASKING_AGAIN = Duration.ofMillis(200);
    final Duration RETRY_WINDOW_NO_LONGER_THAN_ONE_OCSP_READ_TIMEOUT = Duration.ofSeconds(5);
    var retryTemplate =
        new RetryTemplate(
            RetryPolicy.builder()
                .includes(RestClientException.class)
                .excludes(HttpClientErrorException.class)
                .maxRetries(RETRIES_WHILE_THE_PERSON_WAITS)
                .delay(DELAY_BEFORE_ASKING_AGAIN)
                .timeout(RETRY_WINDOW_NO_LONGER_THAN_ONE_OCSP_READ_TIMEOUT)
                .build());
    retryTemplate.setRetryListener(new LoggingEachOcspRetry());
    return retryTemplate;
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

  private static class LoggingEachOcspRetry implements RetryListener {
    @Override
    public void beforeRetry(
        RetryPolicy retryPolicy, Retryable<?> retryable, RetryState retryState) {
      log.warn(
          "Smart-ID OCSP responder failed, asking again: attempt={}, reason={}",
          retryState.getRetryCount() + 1,
          getMostSpecificCause(retryState.getLastException()).getClass().getSimpleName());
    }
  }
}
