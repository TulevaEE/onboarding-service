package ee.tuleva.onboarding.analytics.exchange;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledExchangeTransactionSynchronizationJobTest {

  @Mock private ExchangeTransactionSynchronizer synchronizer;

  @InjectMocks private ScheduledExchangeTransactionSynchronizationJob job;

  @Test
  void run_shouldCallSyncTransactionsWithCorrectDates() {
    LocalDate fixedNow = LocalDate.of(2025, 3, 17);

    try (MockedStatic<LocalDate> localDateMock = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
      localDateMock.when(LocalDate::now).thenReturn(fixedNow);

      job.run();

      verify(synchronizer)
          .syncTransactions(
              eq(fixedNow.minusDays(2)), eq(Optional.empty()), eq(Optional.empty()), eq(false));
    }
  }

  @Test
  void runInitialTransactionsSync_shouldCallSyncTransactionsWithFixedDate() {
    job.runInitialTransactionsSync();

    verify(synchronizer)
        .syncTransactions(
            eq(LocalDate.of(2025, 1, 1)), eq(Optional.empty()), eq(Optional.empty()), eq(false));
  }
}
