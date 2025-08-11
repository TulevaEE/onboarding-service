package ee.tuleva.onboarding.analytics.transaction.exchange;

import static java.time.ZoneOffset.UTC;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.time.FixedClockConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledExchangeTransactionSynchronizationJobTest extends FixedClockConfig {

  @Mock private ExchangeTransactionSynchronizer exchangeTransactionSynchronizer;
  @Mock private MandateDeadlinesService mandateDeadlinesService;
  @Mock private Clock clock;

  @InjectMocks private ScheduledExchangeTransactionSynchronizationJob job;

  @Test
  void run_callsSynchronizerWithPeriodStartDateForYesterday() {
    // given
    when(clock.instant())
        .thenReturn(Instant.parse("2025-08-01T10:00:00Z")); // First day of new period
    LocalDate yesterday = LocalDate.of(2025, 7, 31); // Last day of previous period
    LocalDate expectedStartDate = LocalDate.of(2025, 4, 1); // Start of previous period
    when(mandateDeadlinesService.getPeriodStartDate(yesterday)).thenReturn(expectedStartDate);

    // when
    job.run();

    // then
    verify(mandateDeadlinesService).getPeriodStartDate(yesterday);
    verify(exchangeTransactionSynchronizer)
        .sync(eq(expectedStartDate), eq(Optional.empty()), eq(Optional.empty()), eq(false));
    verifyNoMoreInteractions(exchangeTransactionSynchronizer, mandateDeadlinesService);
  }

  @Test
  void run_callsSynchronizerWithCorrectPeriodStartDateMidPeriod() {
    // given
    when(clock.instant()).thenReturn(Instant.parse("2025-05-15T10:00:00Z")); // Mid-period
    LocalDate yesterday = LocalDate.of(2025, 5, 14);
    LocalDate expectedStartDate = LocalDate.of(2025, 4, 1); // Current period start
    when(mandateDeadlinesService.getPeriodStartDate(yesterday)).thenReturn(expectedStartDate);

    // when
    job.run();

    // then
    verify(mandateDeadlinesService).getPeriodStartDate(yesterday);
    verify(exchangeTransactionSynchronizer)
        .sync(eq(expectedStartDate), eq(Optional.empty()), eq(Optional.empty()), eq(false));
    verifyNoMoreInteractions(exchangeTransactionSynchronizer, mandateDeadlinesService);
  }

  @BeforeEach
  void setUp() {
    when(clock.getZone()).thenReturn(UTC);
  }
}
