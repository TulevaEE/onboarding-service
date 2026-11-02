package ee.tuleva.onboarding.aml.risklevel;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpServer;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.RiskLevels;
import ee.tuleva.onboarding.time.ClockConfig;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RiskLevelsWiringTest {

  private static final String PERSONAL_CODE = "38888888888";
  private static final String API_TOKEN = "test-api-token-of-at-least-thirty-two-characters";
  private static final String CUSTOMIZED_HEADER = "X-Seen-By-A-Customizer";

  private final List<String> requestedPaths = new CopyOnWriteArrayList<>();
  private final List<String> requestsSeenByCustomizers = new CopyOnWriteArrayList<>();
  private HttpServer amlService;

  @BeforeEach
  void startAmlService() throws IOException {
    amlService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    amlService.createContext(
        "/",
        exchange -> {
          requestedPaths.add(exchange.getRequestURI().getPath());
          if (exchange.getRequestHeaders().containsKey(CUSTOMIZED_HEADER)) {
            requestsSeenByCustomizers.add(exchange.getRequestURI().getPath());
          }
          var body = "{\"families\": [{\"family\": \"tkf\", \"effective\": \"HIGH\"}]}";
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.getBytes(UTF_8).length);
          exchange.getResponseBody().write(body.getBytes(UTF_8));
          exchange.close();
        });
    amlService.start();
  }

  @AfterEach
  void stopAmlService() {
    amlService.stop(0);
  }

  @Test
  void asksAmlServiceForTheEffectiveLevelsWhenHighRiskIsEnabled() {
    contextRunner()
        .withPropertyValues("aml-service.high-risk-enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(RiskLevels.class);
              assertThat(context.getBean(RiskLevels.class).isHighRisk(PERSONAL_CODE)).isTrue();
              assertThat(requestedPaths)
                  .containsExactly("/v1/risk-levels/effective/" + PERSONAL_CODE);
            });
  }

  @Test
  void keepsThePersonalCodeInTheUrlAwayFromTheApplicationsRestClientCustomizers() {
    contextRunner()
        .withPropertyValues("aml-service.high-risk-enabled=true")
        .run(
            context -> {
              context.getBean(RiskLevels.class).isHighRisk(PERSONAL_CODE);

              assertThat(requestedPaths)
                  .containsExactly("/v1/risk-levels/effective/" + PERSONAL_CODE);
              assertThat(requestsSeenByCustomizers).isEmpty();
            });
  }

  @Test
  void readsTheLatestAmlChecksWithoutAskingAmlServiceWhenHighRiskIsNotEnabled() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(RiskLevels.class);
              assertThat(context.getBean(RiskLevels.class).isHighRisk(PERSONAL_CODE)).isFalse();
              assertThat(requestedPaths).isEmpty();
            });
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(
            ClockConfig.class,
            AmlServiceConfiguration.class,
            EffectiveRiskLevelConfiguration.class,
            EffectiveRiskLevels.class,
            AmlCheckRiskLevels.class,
            ConfiguredRiskLevels.class)
        .withBean(AmlCheckRepository.class, () -> mock(AmlCheckRepository.class))
        .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
        .withBean(
            RestClientCustomizer.class,
            () -> builder -> builder.defaultHeader(CUSTOMIZED_HEADER, "yes"))
        .withPropertyValues(
            "aml-service.url=http://127.0.0.1:" + amlService.getAddress().getPort(),
            "aml-service.api-token=" + API_TOKEN,
            "aml-service.connect-timeout=5s",
            "aml-service.read-timeout=60s",
            "aml-service.high-risk-connect-timeout=1s",
            "aml-service.high-risk-read-timeout=2s");
  }
}
