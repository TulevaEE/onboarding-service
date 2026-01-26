package ee.tuleva.onboarding.investment.fees;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeCalculationScheduledJobTest {

  @Mock private FeeCalculationService feeCalculationService;

  private FeeCalculationScheduledJob scheduledJob;

  @BeforeEach
  void setUp() {
    scheduledJob = new FeeCalculationScheduledJob(feeCalculationService);
  }

  @AfterEach
  void cleanup() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void calculateDailyFees_calculatesForYesterday() {
    LocalDate today = LocalDate.of(2025, 1, 16);
    LocalDate yesterday = LocalDate.of(2025, 1, 15);
    ClockHolder.setClock(Clock.fixed(today.atStartOfDay(UTC).toInstant(), UTC));

    scheduledJob.calculateDailyFees();

    verify(feeCalculationService).calculateDailyFees(yesterday);
  }

  @Test
  void calculateDailyFees_rethrowsException() {
    LocalDate today = LocalDate.of(2025, 1, 16);
    LocalDate yesterday = LocalDate.of(2025, 1, 15);
    ClockHolder.setClock(Clock.fixed(today.atStartOfDay(UTC).toInstant(), UTC));
    doThrow(new RuntimeException("Database error"))
        .when(feeCalculationService)
        .calculateDailyFees(yesterday);

    assertThatThrownBy(() -> scheduledJob.calculateDailyFees())
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void calculateDailyFees_handlesMonthBoundary() {
    LocalDate firstOfMonth = LocalDate.of(2025, 2, 1);
    LocalDate lastDayOfPreviousMonth = LocalDate.of(2025, 1, 31);
    ClockHolder.setClock(Clock.fixed(firstOfMonth.atStartOfDay(UTC).toInstant(), UTC));

    scheduledJob.calculateDailyFees();

    verify(feeCalculationService).calculateDailyFees(lastDayOfPreviousMonth);
  }
}
