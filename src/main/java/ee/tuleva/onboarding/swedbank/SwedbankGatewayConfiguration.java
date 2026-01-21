package ee.tuleva.onboarding.swedbank;

import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.statement.StatementRequestMessageGenerator;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankMessageReceiver;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankStatementFetcher;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.listener.SwedbankBankStatementListener;
import ee.tuleva.onboarding.swedbank.listener.SwedbankReconciliationListener;
import ee.tuleva.onboarding.swedbank.processor.SwedbankBankStatementProcessor;
import ee.tuleva.onboarding.swedbank.reconcillation.Reconciliator;
import java.io.File;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import lombok.RequiredArgsConstructor;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties({
  SwedbankGatewayProperties.class,
  SwedbankAccountConfiguration.class
})
@ConditionalOnProperty(prefix = "swedbank-gateway", name = "enabled", havingValue = "true")
@Import({
  SwedbankGatewayClient.class,
  SwedbankPaymentRequestListener.class,
  SwedbankBankStatementListener.class,
  SwedbankReconciliationListener.class,
  SwedbankBankStatementProcessor.class,
  Reconciliator.class
})
public class SwedbankGatewayConfiguration {

  private final SwedbankGatewayProperties properties;

  @Bean
  @Qualifier("swedbankGatewayRestTemplate")
  @SneakyThrows
  public RestTemplate swedbankGatewayRestTemplate() {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(
        new File(properties.keystore().path()).toURI().toURL().openStream(),
        properties.keystore().password().toCharArray());

    SSLContext sslContext =
        SSLContexts.custom()
            .loadKeyMaterial(keyStore, properties.keystore().password().toCharArray())
            .build();

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

  @Bean
  @Profile("!staging")
  SwedbankMessageReceiver swedbankMessageReceiver(
      BankingMessageRepository bankingMessageRepository,
      SwedbankGatewayClient swedbankGatewayClient) {
    return new SwedbankMessageReceiver(bankingMessageRepository, swedbankGatewayClient);
  }

  @Bean
  @Profile("!staging")
  SwedbankStatementFetcher swedbankStatementFetcher(
      SwedbankGatewayClient swedbankGatewayClient,
      SwedbankAccountConfiguration swedbankAccountConfiguration,
      StatementRequestMessageGenerator statementRequestMessageGenerator) {
    return new SwedbankStatementFetcher(
        swedbankGatewayClient, swedbankAccountConfiguration, statementRequestMessageGenerator);
  }
}
