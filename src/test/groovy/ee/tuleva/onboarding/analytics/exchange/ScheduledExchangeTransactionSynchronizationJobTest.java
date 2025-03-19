package ee.tuleva.onboarding.analytics.exchange;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledExchangeTransactionSynchronizationJobTest {

  @Mock private ExchangeTransactionSynchronizer synchronizer;

  @Mock private MandateDeadlinesService mandateDeadlinesService;

  @InjectMocks private ScheduledExchangeTransactionSynchronizationJob job;

  @Test
  void run_shouldCallSyncTransactionsWithCurrentPeriodStartDate() {
    LocalDate currentPeriodStart = LocalDate.of(2020, 12, 1);
    when(mandateDeadlinesService.getCurrentPeriodStartDate()).thenReturn(currentPeriodStart);

    job.run();

    verify(synchronizer)
        .syncTransactions(
            eq(currentPeriodStart), eq(Optional.empty()), eq(Optional.empty()), eq(false));
  }

  @Test
  void runInitialTransactionsSync_shouldCallSyncTransactionsWithFixedDate() {
    job.runInitialTransactionsSync();

    verify(synchronizer)
        .syncTransactions(
            eq(LocalDate.of(2024, 12, 1)), eq(Optional.empty()), eq(Optional.empty()), eq(false));
  }
}
