package ee.tuleva.onboarding.analytics.paymentrate;

import static ee.tuleva.onboarding.notification.email.EmailType.PAYMENT_RATE_ABANDONMENT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class PaymentRateAbandonmentRepositoryTest {

  private final PaymentRateAbandonmentRepository repository =
      new PaymentRateAbandonmentRepository((JdbcClient) null);

  @Test
  void getEmailTypeIsPaymentRateAbandonment() {
    assertThat(repository.getEmailType()).isEqualTo(PAYMENT_RATE_ABANDONMENT);
  }
}
