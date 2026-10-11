package ee.tuleva.onboarding.accounting.directo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class DirectoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(DirectoConfiguration.class)
          .withBean(RestClient.Builder.class, RestClient::builder)
          .withPropertyValues("directo.url=https://directo.test/apidirect");

  @Test
  void retriesAServerErrorWithTheProductionPolicy() {
    var attempts = new AtomicInteger();

    contextRunner
        .withPropertyValues("directo.retry-delay=1ms")
        .run(
            context -> {
              var retryTemplate = context.getBean("directoRetryTemplate", RetryTemplate.class);

              var result =
                  retryTemplate.invoke(
                      () -> {
                        if (attempts.incrementAndGet() < 2) {
                          throw HttpServerErrorException.create(
                              BAD_GATEWAY, "", HttpHeaders.EMPTY, new byte[0], null);
                        }
                        return "fetched";
                      });

              assertThat(result).isEqualTo("fetched");
              assertThat(attempts).hasValue(2);
            });
  }

  @Test
  void aMissingKeyLetsTheApplicationStartAndRefusesAtCallTime() {
    contextRunner
        .withPropertyValues("directo.retry-delay=1ms", "directo.api-keys[TULEVA_FONDID]=")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var client = context.getBean(DirectoClient.class);
              assertThat(client.entities()).containsExactly("TULEVA_FONDID");
              assertThatThrownBy(() -> client.fetchBook("TULEVA_FONDID"))
                  .isInstanceOf(IllegalStateException.class);
            });
  }

  @Test
  void theApplicationConfigurationReadsEachEntityKeyFromItsEnvironmentVariable() {
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(DirectoConfiguration.class)
        .withBean(RestClient.Builder.class, RestClient::builder)
        .withSystemProperties("DIRECTO_API_KEY_TULEVA_FONDID=configured-key")
        .run(
            context ->
                assertThat(context.getBean(DirectoProperties.class).apiKeys())
                    .isEqualTo(Map.of("TULEVA_FONDID", "configured-key")));
  }
}
