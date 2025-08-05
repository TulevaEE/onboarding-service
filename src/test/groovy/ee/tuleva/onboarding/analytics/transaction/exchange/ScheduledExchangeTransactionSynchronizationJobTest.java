package ee.tuleva.onboarding.analytics.transaction.exchange;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.time.FixedClockConfig;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledExchangeTransactionSynchronizationJobTest extends FixedClockConfig {

  @Mock private ExchangeTransactionSynchronizer exchangeTransactionSynchronizer;
  @Mock private MandateDeadlinesService mandateDeadlinesService;

  @InjectMocks private ScheduledExchangeTransactionSynchronizationJob job;

  @Test
  void run_callsSynchronizerWithPeriodStartDateForYesterday() {
    // given
    LocalDate today = LocalDate.of(2025, 8, 1); // First day of new period
    LocalDate yesterday = LocalDate.of(2025, 7, 31); // Last day of previous period
    LocalDate expectedStartDate = LocalDate.of(2025, 4, 1); // Start of previous period

    try (MockedStatic<LocalDate> mockedLocalDate =
        mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
      mockedLocalDate.when(LocalDate::now).thenReturn(today);
      when(mandateDeadlinesService.getPeriodStartDate(yesterday)).thenReturn(expectedStartDate);

      // when
      job.run();

      // then
      verify(mandateDeadlinesService).getPeriodStartDate(yesterday);
      verify(exchangeTransactionSynchronizer)
          .sync(eq(expectedStartDate), eq(Optional.empty()), eq(Optional.empty()), eq(false));
      verifyNoMoreInteractions(exchangeTransactionSynchronizer, mandateDeadlinesService);
    }
  }

  @Test
  void run_callsSynchronizerWithCorrectPeriodStartDateMidPeriod() {
    // given
    LocalDate today = LocalDate.of(2025, 5, 15); // Mid-period
    LocalDate yesterday = LocalDate.of(2025, 5, 14);
    LocalDate expectedStartDate = LocalDate.of(2025, 4, 1); // Current period start

    try (MockedStatic<LocalDate> mockedLocalDate =
        mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
      mockedLocalDate.when(LocalDate::now).thenReturn(today);
      when(mandateDeadlinesService.getPeriodStartDate(yesterday)).thenReturn(expectedStartDate);

      // when
      job.run();

      // then
      verify(mandateDeadlinesService).getPeriodStartDate(yesterday);
      verify(exchangeTransactionSynchronizer)
          .sync(eq(expectedStartDate), eq(Optional.empty()), eq(Optional.empty()), eq(false));
      verifyNoMoreInteractions(exchangeTransactionSynchronizer, mandateDeadlinesService);
    }
  }
}
