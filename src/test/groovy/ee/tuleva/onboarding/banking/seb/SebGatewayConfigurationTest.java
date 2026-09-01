package ee.tuleva.onboarding.banking.seb;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.mock;

import com.sun.net.httpserver.HttpServer;
import ee.tuleva.onboarding.banking.seb.fetcher.SebStatementFetchingScheduler;
import ee.tuleva.onboarding.banking.seb.listener.SebReconciliationListener;
import ee.tuleva.onboarding.banking.seb.reconciliation.SebReconciliator;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class SebGatewayConfigurationTest {

  private static final String KEYSTORE_PATH = "src/test/resources/banking/seb/test-seb-gateway.p12";
  private static final String KEYSTORE_PASSWORD = "testpass";
  private static final String KEY_ALIAS = "test";

  private final SebGatewayConfiguration config =
      new SebGatewayConfiguration(
          new SebGatewayProperties(
              true,
              "https://seb-gateway.example.com",
              new SebGatewayProperties.Keystore(KEYSTORE_PATH, KEYSTORE_PASSWORD),
              Duration.ofSeconds(30)));

  @Test
  void sebGatewayKeyStore_loadsThePkcs12KeystoreFromTheConfiguredPath() throws Exception {
    KeyStore keyStore = config.sebGatewayKeyStore();

    assertThat(keyStore.getType()).isEqualTo("PKCS12");
    assertThat(keyStore.containsAlias(KEY_ALIAS)).isTrue();
  }

  @Test
  void sebHttpSignature_resolvesTheKeystoreEntryAndProducesAWorkingSignature() throws Exception {
    KeyStore keyStore = config.sebGatewayKeyStore();

    SebHttpSignature signature = config.sebHttpSignature(keyStore);

    String digest = signature.createDigest("test-signing-body".getBytes(UTF_8));
    String header = signature.createSignature(digest);
    assertThat(digest).isEqualTo("SHA-256=VjV+H4T0smAd6uGlpbe7mttq1wLWUbmvdhI9ueSiGv0=");
    assertThat(header)
        .isEqualTo(
            "keyId=\"SN=e079cedfa8cf463f,CA=CN=Test,O=Test\",algorithm=\"rsa-sha256\",headers=\"digest\",signature=\"R3ihIxYJjBmVUhLNqF/42VcE5vqFupfxZE/6JMpHFyJaYLAfh4ljCi5kOKY2PPQC646lKB9JU9bX5+ewHXezqu80YGKOBpy8Q44Lsd9s7NXbg83u5DOfTgLzIvCuY+iPDnV5IE/v4s4ijnKP43UstKmkBuVavq1HFjHNMXd3nZ9joKXAocZONqOF3DTKauqn9guAlvRiqdGnMXVXzELoUlUYsOd43HQPj3bTTwp4T5gr/pyCsQg68qKv2sIn6HWu0k46qpsunEVaCQD3kM2/AJ5lGfoXuz6FV0w6/6y4qVQvdOU1L/Ln9XBB50/omIwBzL8klpPTLVe9UjmikV5bCw==\"");
  }

  @Test
  void sebHttpSignature_ignoresTrustedCertificateEntriesWhenFindingTheKeyEntry() throws Exception {
    KeyStore mixedKeyStore = KeyStore.getInstance("PKCS12");
    mixedKeyStore.load(null, null);
    KeyStore realKeyStore = config.sebGatewayKeyStore();
    mixedKeyStore.setKeyEntry(
        KEY_ALIAS,
        realKeyStore.getKey(KEY_ALIAS, KEYSTORE_PASSWORD.toCharArray()),
        KEYSTORE_PASSWORD.toCharArray(),
        new X509Certificate[] {(X509Certificate) realKeyStore.getCertificate(KEY_ALIAS)});
    mixedKeyStore.setCertificateEntry(
        "trusted", SebTestCertificates.selfSigned("CN=OnlyCommon", BigInteger.TEN));

    SebHttpSignature signature = config.sebHttpSignature(mixedKeyStore);

    String header = signature.createSignature("SHA-256=stub-digest");
    assertThat(header).startsWith("keyId=\"SN=e079cedfa8cf463f,CA=CN=Test,O=Test\"");
  }

  @Test
  void getSingleKeyAlias_throwsWhenTheKeystoreHasNoKeyEntries() throws Exception {
    KeyStore emptyKeyStore = KeyStore.getInstance("PKCS12");
    emptyKeyStore.load(null, null);

    assertThatThrownBy(() -> config.sebHttpSignature(emptyKeyStore))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void getSingleKeyAlias_throwsWhenTheKeystoreHasMultipleKeyEntries() throws Exception {
    KeyStore multiKeyStore = KeyStore.getInstance("PKCS12");
    multiKeyStore.load(null, null);
    var firstCertificate = SebTestCertificates.selfSigned("CN=First", BigInteger.ONE);
    var secondCertificate = SebTestCertificates.selfSigned("CN=Second", BigInteger.TWO);
    KeyStore realKeyStore = config.sebGatewayKeyStore();
    var privateKey = realKeyStore.getKey(KEY_ALIAS, KEYSTORE_PASSWORD.toCharArray());
    multiKeyStore.setKeyEntry(
        "alias-a",
        privateKey,
        KEYSTORE_PASSWORD.toCharArray(),
        new X509Certificate[] {firstCertificate});
    multiKeyStore.setKeyEntry(
        "alias-b",
        privateKey,
        KEYSTORE_PASSWORD.toCharArray(),
        new X509Certificate[] {secondCertificate});

    assertThatThrownBy(() -> config.sebHttpSignature(multiKeyStore))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void sebTlsStrategyFactory_createsATlsSocketStrategy() throws Exception {
    SebTlsStrategyFactory factory = config.sebTlsStrategyFactory();

    var strategy = factory.create(SSLContext.getDefault());

    assertThat(strategy).isNotNull();
  }

  @Test
  void sebGatewayRestClient_sendsRequestsThroughTheLoggingInterceptor() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/ping",
        exchange -> {
          byte[] body = "pong".getBytes(UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      var localConfig =
          new SebGatewayConfiguration(
              new SebGatewayProperties(
                  true,
                  "http://localhost:" + server.getAddress().getPort(),
                  new SebGatewayProperties.Keystore(KEYSTORE_PATH, KEYSTORE_PASSWORD),
                  Duration.ofSeconds(30)));
      KeyStore keyStore = localConfig.sebGatewayKeyStore();
      RestClient client =
          localConfig.sebGatewayRestClient(keyStore, localConfig.sebTlsStrategyFactory());

      String response = client.get().uri("/ping").retrieve().body(String.class);

      assertThat(response).isEqualTo("pong");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void sebGatewayRetryTemplate_retriesIncludedExceptionsAndSucceeds() {
    RetryTemplate retryTemplate = config.sebGatewayRetryTemplate();
    AtomicInteger attempts = new AtomicInteger();

    String result =
        retryTemplate.invoke(
            () -> {
              if (attempts.incrementAndGet() < 3) {
                throw new ResourceAccessException("simulated failure");
              }
              return "ok";
            });

    assertThat(result).isEqualTo("ok");
    assertThat(attempts.get()).isEqualTo(3);
  }

  @Test
  void sebGatewayRetryTemplate_doesNotRetryExcludedClientErrors() {
    RetryTemplate retryTemplate = config.sebGatewayRetryTemplate();
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                retryTemplate.invoke(
                    () -> {
                      attempts.incrementAndGet();
                      throw HttpClientErrorException.create(
                          org.springframework.http.HttpStatus.BAD_REQUEST,
                          "Bad Request",
                          null,
                          null,
                          null);
                    }))
        .isInstanceOf(HttpClientErrorException.class);
    assertThat(attempts.get()).isEqualTo(1);
  }

  @Test
  void sebStatementFetchingScheduler_isConstructedFromThePublisherAndAccounts() {
    var eventPublisher = mock(ApplicationEventPublisher.class);
    var bankAccounts = mock(SebBankAccounts.class);

    SebStatementFetchingScheduler scheduler =
        config.sebStatementFetchingScheduler(eventPublisher, bankAccounts);

    assertThat(scheduler).isNotNull();
  }

  @Test
  void sebReconciliationListener_isConstructedFromReconciliatorSchedulerAndClock() {
    var reconciliator = mock(SebReconciliator.class);
    var taskScheduler = mock(TaskScheduler.class);
    Clock clock = Clock.systemUTC();

    SebReconciliationListener listener =
        config.sebReconciliationListener(reconciliator, taskScheduler, clock);

    assertThat(listener).isNotNull();
  }
}
