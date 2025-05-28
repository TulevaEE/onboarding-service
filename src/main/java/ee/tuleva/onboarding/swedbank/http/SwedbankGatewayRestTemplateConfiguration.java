package ee.tuleva.onboarding.swedbank.http;

import java.io.File;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@Slf4j
public class SwedbankGatewayRestTemplateConfiguration {

  @Value("${swedbank-gateway.keystore.path}")
  private String keystorePath;

  @Value("${swedbank-gateway.keystore.password}")
  private String keystorePassword;

  @Bean
  @Qualifier("swedbankGatewayRestTemplate")
  @SneakyThrows
  public RestTemplate swedbankGatewayRestTemplate() {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(
        new File(keystorePath).toURI().toURL().openStream(), keystorePassword.toCharArray());

    SSLContext sslContext =
        SSLContexts.custom().loadKeyMaterial(keyStore, keystorePassword.toCharArray()).build();

    SSLConnectionSocketFactory sslConFactory =
        SSLConnectionSocketFactoryBuilder.create().setSslContext(sslContext).build();
    HttpClientConnectionManager cm =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setSSLSocketFactory(sslConFactory)
            .build();

    CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(cm).build();
    ClientHttpRequestFactory requestFactory =
        new HttpComponentsClientHttpRequestFactory(httpClient);

    var restTemplate = new RestTemplate(requestFactory);

    restTemplate
        .getInterceptors()
        .add(
            (request, body, execution) -> {
              log.info("Sending {} request to {}", request.getMethod(), request.getURI());
              return execution.execute(request, body);
            });

    return restTemplate;
  }
}
