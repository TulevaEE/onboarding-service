package ee.tuleva.onboarding.analytics.transaction.exchange;

import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.time.FixedClockConfig;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledExchangeTransactionSynchronizationJobTest extends FixedClockConfig {

  @Mock private ExchangeTransactionSynchronizer exchangeTransactionSynchronizer;
  @Mock private MandateDeadlinesService mandateDeadlinesService;

  @InjectMocks private ScheduledExchangeTransactionSynchronizationJob job;

  @Test
  void run_callsSynchronizerWithStartDateFromMandateService() {
    // given
    LocalDate expectedStartDate = LocalDate.of(2025, 4, 1);
    when(mandateDeadlinesService.getCurrentPeriodStartDate()).thenReturn(expectedStartDate);

    // when
    job.run();

    // then
    verify(mandateDeadlinesService).getCurrentPeriodStartDate();
    verify(exchangeTransactionSynchronizer)
        .sync(eq(expectedStartDate), eq(Optional.empty()), eq(Optional.empty()), eq(false));
    verifyNoMoreInteractions(exchangeTransactionSynchronizer, mandateDeadlinesService);
  }
}
