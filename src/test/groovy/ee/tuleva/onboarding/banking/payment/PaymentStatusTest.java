package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.PaymentStatus.CANCELLED;
import static ee.tuleva.onboarding.banking.payment.PaymentStatus.REJECTED;
import static ee.tuleva.onboarding.banking.payment.PaymentStatus.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentStatusTest {

  @Test
  void aCancelledTransactionIsReadAsCancelledRatherThanUnknown() {
    assertThat(PaymentStatus.from("CANC")).isEqualTo(CANCELLED);
  }

  @Test
  void aRejectedTransactionIsReadAsRejected() {
    assertThat(PaymentStatus.from("RJCT")).isEqualTo(REJECTED);
  }

  @Test
  void theCodeIsReadRegardlessOfCaseAndSurroundingWhitespace() {
    assertThat(PaymentStatus.from(" canc ")).isEqualTo(CANCELLED);
  }

  @Test
  void anUnrecognisedCodeIsUnknown() {
    assertThat(PaymentStatus.from("WHAT")).isEqualTo(UNKNOWN);
  }
}
