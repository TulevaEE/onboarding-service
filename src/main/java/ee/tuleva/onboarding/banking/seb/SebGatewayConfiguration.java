package ee.tuleva.onboarding.banking.seb;

import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import javax.net.ssl.SSLContext;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(SebGatewayProperties.class)
@ConditionalOnProperty(prefix = "seb-gateway", name = "enabled", havingValue = "true")
public class SebGatewayConfiguration {

  private final SebGatewayProperties properties;

  @Bean
  @SneakyThrows
  public KeyStore sebGatewayKeyStore() {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(
        new File(properties.keystore().path()).toURI().toURL().openStream(),
        properties.keystore().password().toCharArray());
    return keyStore;
  }

  @Bean
  @SneakyThrows
  public SebHttpSignature sebHttpSignature(KeyStore sebGatewayKeyStore) {
    PrivateKey privateKey = extractPrivateKey(sebGatewayKeyStore);
    java.security.cert.X509Certificate certificate = extractCertificate(sebGatewayKeyStore);
    String keyId = SebHttpSignature.buildKeyId(certificate);
    log.info("SEB Gateway HTTP Signature keyId: {}", keyId);
    return new SebHttpSignature(privateKey, keyId);
  }

  @SneakyThrows
  private X509Certificate extractCertificate(KeyStore keyStore) {
    return (X509Certificate) keyStore.getCertificate(getSingleKeyAlias(keyStore));
  }

  @SneakyThrows
  private String getSingleKeyAlias(KeyStore keyStore) {
    var keyAliases =
        Collections.list(keyStore.aliases()).stream()
            .filter(alias -> isKeyEntry(keyStore, alias))
            .toList();

    if (keyAliases.isEmpty()) {
      throw new IllegalStateException("No key entry found in keystore");
    }
    if (keyAliases.size() > 1) {
      throw new IllegalStateException(
          "Multiple key entries in keystore: " + String.join(", ", keyAliases));
    }
    return keyAliases.getFirst();
  }

  @SneakyThrows
  private boolean isKeyEntry(KeyStore keyStore, String alias) {
    return keyStore.isKeyEntry(alias);
  }

  @Bean
  SebTlsStrategyFactory sebTlsStrategyFactory() {
    return DefaultClientTlsStrategy::new;
  }

  @Bean
  @SneakyThrows
  public RestClient sebGatewayRestClient(
      KeyStore sebGatewayKeyStore, SebTlsStrategyFactory tlsStrategyFactory) {
    SSLContext sslContext =
        SSLContexts.custom()
            .loadKeyMaterial(sebGatewayKeyStore, properties.keystore().password().toCharArray())
            .build();

    var connectionManager =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setTlsSocketStrategy(tlsStrategyFactory.create(sslContext))
            .build();

    var httpClient = HttpClients.custom().setConnectionManager(connectionManager).build();

    HttpComponentsClientHttpRequestFactory requestFactory =
        new HttpComponentsClientHttpRequestFactory(httpClient);

    return RestClient.builder()
        .baseUrl(properties.url())
        .requestFactory(requestFactory)
        .defaultHeader("OrgId", properties.orgId())
        .defaultHeader("Accept", APPLICATION_XML_VALUE)
        .requestInterceptor(
            (request, body, execution) -> {
              log.info("SEB Gateway: {} {}", request.getMethod(), request.getURI());
              return execution.execute(request, body);
            })
        .build();
  }

  @SneakyThrows
  private PrivateKey extractPrivateKey(KeyStore keyStore) {
    return (PrivateKey)
        keyStore.getKey(
            getSingleKeyAlias(keyStore), properties.keystore().password().toCharArray());
  }
}

@ConfigurationProperties(prefix = "seb-gateway")
record SebGatewayProperties(boolean enabled, String url, String orgId, Keystore keystore) {
  record Keystore(String path, String password) {}
}

@FunctionalInterface
interface SebTlsStrategyFactory {
  org.apache.hc.client5.http.ssl.TlsSocketStrategy create(SSLContext sslContext);
}
