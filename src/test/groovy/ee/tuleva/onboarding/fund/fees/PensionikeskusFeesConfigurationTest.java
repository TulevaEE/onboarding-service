package ee.tuleva.onboarding.fund.fees;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class PensionikeskusFeesConfigurationTest {

  private final PensionikeskusFeesConfiguration configuration =
      new PensionikeskusFeesConfiguration();

  @Test
  void buildsARestClient() {
    assertThat(configuration.pensionikeskusFeesRestClient(RestClient.builder())).isNotNull();
  }

  @Test
  void retryTemplateRetriesServerErrors() {
    var attempts = new AtomicInteger();
    var template = configuration.pensionikeskusFeesRetryTemplate();

    var result =
        template.invoke(
            () -> {
              if (attempts.incrementAndGet() < 2) {
                throw HttpServerErrorException.create(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "server error",
                    org.springframework.http.HttpHeaders.EMPTY,
                    new byte[0],
                    null);
              }
              return "ok";
            });

    assertThat(result).isEqualTo("ok");
    assertThat(attempts.get()).isEqualTo(2);
  }
}
