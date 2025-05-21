package ee.tuleva.onboarding.swedbank.http;

import lombok.SneakyThrows;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContexts;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.security.KeyStore;

@Configuration
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
    keyStore.load(new File("client-keystore.p12").toURI().toURL().openStream(), keystorePassword.toCharArray());

    /*KeyStore trustStore = KeyStore.getInstance("JKS");
    trustStore.load(new File("truststore.jks").toURI().toURL().openStream(), "truststore-password".toCharArray());*/

    SSLContext sslContext = SSLContexts.custom()
        .loadKeyMaterial(keyStore, "keystore-password".toCharArray())
        // .loadTrustMaterial(trustStore, null)
        .build();


    SSLConnectionSocketFactory sslConFactory = new SSLConnectionSocketFactory(sslContext);
    HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
        .setSSLSocketFactory(sslConFactory)
        .build();

    CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(cm).build();
    ClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
    return new RestTemplate(requestFactory);

  }

}
