package ee.tuleva.onboarding.aml.sanctions;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.country.Country;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.json.JsonMapper;

class OpenSanctionsServiceTimeoutTest {

  @Test
  @Timeout(value = 5, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void match_failsFastWhenSanctionsApiDoesNotRespond() throws IOException {
    try (var unresponsiveServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      var service =
          new OpenSanctionsService(
              new RestTemplateBuilder(),
              JsonMapper.builder().build(),
              "http://127.0.0.1:" + unresponsiveServer.getLocalPort(),
              Duration.ofMillis(500),
              Duration.ofMillis(250));
      var person = new PersonImpl("36004081234", "Peeter", "Meeter");

      Throwable thrown = catchThrowable(() -> service.match(person, new Country("ee")));

      assertThat(thrown).isInstanceOf(ResourceAccessException.class);
    }
  }
}
