package ee.tuleva.onboarding.notification.email.firstpayment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FirstThirdPillarPaymentTest {

  private static final String PERSONAL_CODE = TestPersonalCodes.withValidChecksum("3860101000");

  private FirstThirdPillarPayment payment() {
    return new FirstThirdPillarPayment(
        PERSONAL_CODE,
        "First",
        "Last",
        "first.last@example.com",
        "EST",
        new BigDecimal("100.00"),
        LocalDate.parse("2026-08-16"),
        true,
        true,
        true,
        true,
        false,
        false);
  }

  @Test
  void exposesTheFirstNameThroughThePersonInterface() {
    assertThat(payment().getFirstName()).isEqualTo("First");
  }

  @Test
  void exposesTheLastNameThroughThePersonInterface() {
    assertThat(payment().getLastName()).isEqualTo("Last");
  }
}
