package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RECEIVED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;
import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StuckPaymentAlertJobTest {

  private static final Instant NOW = Instant.parse("2025-10-01T12:00:00Z");

  SavingFundPaymentRepository paymentRepository = mock(SavingFundPaymentRepository.class);
  Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  StuckPaymentAlertJob job = new StuckPaymentAlertJob(paymentRepository, clock);

  @Test
  void reportsPaymentsStalledInJobDrivenStatusesForOverHalfAnHour() {
    given(paymentRepository.findStuckPayments(any(), any(), any()))
        .willReturn(List.of(stuckPayment(randomUUID())));

    job.runJob();

    verify(paymentRepository)
        .findStuckPayments(NOW.minus(Duration.ofMinutes(30)), RECEIVED, TO_BE_RETURNED);
  }

  private SavingFundPayment stuckPayment(UUID paymentId) {
    return SavingFundPayment.builder()
        .id(paymentId)
        .amount(new BigDecimal("100.00"))
        .status(RECEIVED)
        .statusChangedAt(NOW.minus(Duration.ofHours(3)))
        .build();
  }
}
