package ee.tuleva.onboarding.config;

import ee.sk.mid.MidAuthenticationResponseValidator;
import ee.sk.mid.MidClient;
import ee.sk.mid.rest.MidConnector;
import ee.sk.mid.rest.MidSessionStatusPoller;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@Configuration
@Slf4j
public class MobileIdConfiguration {

  @Value("${truststore.path}")
  private String trustStorePath;

  @Value("${smartid.relyingPartyUUID}")
  private String relyingPartyUUID;

  @Value("${smartid.relyingPartyName}")
  private String relyingPartyName;

  @Value("${mobile-id.hostUrl}")
  private String hostUrl;

  @Value("${mobile-id.pollingSleepTimeoutSeconds}")
  private int pollingSleepTimeoutSeconds;

  @Bean
  @SneakyThrows
  MidClient mobileIDClient(ResourceLoader resourceLoader) {
    return MidClient.newBuilder()
        .withRelyingPartyName(relyingPartyName)
        .withRelyingPartyUUID(relyingPartyUUID)
        .withHostUrl(hostUrl)
        .withPollingSleepTimeoutSeconds(pollingSleepTimeoutSeconds)
        .withTrustStore(getTrustStore(resourceLoader))
        .build();
  }

  @Bean
  MidConnector mobileIDConnector(MidClient mobileIDClient) {
    return mobileIDClient.getMobileIdConnector();
  }

  @Bean
  MidSessionStatusPoller mobileIDSessionStatusPoller(MidClient mobileIDClient) {
    return mobileIDClient.getSessionStatusPoller();
  }

  @Bean
  @SneakyThrows
  MidAuthenticationResponseValidator mobileIDValidator(ResourceLoader resourceLoader) {
    KeyStore trustStore = getTrustStore(resourceLoader);
    return new MidAuthenticationResponseValidator(trustStore);
  }

  private KeyStore getTrustStore(ResourceLoader resourceLoader)
      throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
    Resource resource = resourceLoader.getResource("file:" + trustStorePath);
    InputStream inputStream = resource.getInputStream();
    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    trustStore.load(inputStream, null);
    return trustStore;
  }
}
