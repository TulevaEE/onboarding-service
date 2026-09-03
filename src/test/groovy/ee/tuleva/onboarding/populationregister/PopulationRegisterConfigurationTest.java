package ee.tuleva.onboarding.populationregister;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class PopulationRegisterConfigurationTest {

  private final PopulationRegisterConfiguration configuration =
      new PopulationRegisterConfiguration();

  @Test
  void buildsARestClient() {
    var properties = properties();
    assertThat(configuration.populationRegisterRestClient(RestClient.builder(), properties))
        .isNotNull();
  }

  @Test
  void retryTemplateRetriesServerErrors() {
    var attempts = new AtomicInteger();
    var template = configuration.populationRegisterRetryTemplate();

    var result =
        template.invoke(
            () -> {
              if (attempts.incrementAndGet() < 2) {
                throw HttpServerErrorException.create(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "server error",
                    HttpHeaders.EMPTY,
                    new byte[0],
                    null);
              }
              return "ok";
            });

    assertThat(result).isEqualTo("ok");
    assertThat(attempts.get()).isEqualTo(2);
  }

  private static PopulationRegisterProperties properties() {
    return new PopulationRegisterProperties("http://population-register.test", "client-id");
  }
}
