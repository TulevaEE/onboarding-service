package ee.tuleva.onboarding.nudge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PaymentRateRedirectPropertiesTest {

  private static final BigDecimal THRESHOLD = new BigDecimal("3300");
  private static final LocalDate START = LocalDate.of(2026, 9, 1);

  @Test
  void aBlankSeedFailsAtStartupNotAtTheFirstRequest() {
    assertThatThrownBy(() -> new PaymentRateRedirectProperties(" ", 20, THRESHOLD, START))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new PaymentRateRedirectProperties(null, 20, THRESHOLD, START))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aHoldoutOutsideZeroToHundredFailsAtStartup() {
    assertThatThrownBy(() -> new PaymentRateRedirectProperties("seed", 101, THRESHOLD, START))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aValidConfigurationExposesItsSeed() {
    assertThat(new PaymentRateRedirectProperties("seed", 20, THRESHOLD, START).activeSeed())
        .isEqualTo("seed");
  }
}
