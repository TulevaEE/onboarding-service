package ee.tuleva.onboarding.investment.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class OrderSendAccessNotConnectedTest {

  @ParameterizedTest
  @ValueSource(strings = {"operator-1", "operator-7"})
  void refusesEverySendAsUnavailableUntilTheAccessLevelsAreConnected(String operator) {
    assertThatThrownBy(() -> new OrderSendAccessNotConnected().senderFor(operator))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE));
  }
}
