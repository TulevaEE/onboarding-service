package ee.tuleva.onboarding.analytics.thirdpillar.synchronization;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledThirdPillarTransactionSynchronizationJobTest {

  @Mock private ThirdPillarTransactionSynchronizer thirdPillarTransactionSynchronizer;

  @InjectMocks private ScheduledThirdPillarTransactionSynchronizationJob job;

  @Test
  void run_shouldCallSyncTransactionsWithCorrectDates() {
    LocalDate fixedNow = LocalDate.of(2025, 3, 6);
    try (MockedStatic<LocalDate> localDateMock = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
      localDateMock.when(LocalDate::now).thenReturn(fixedNow);

      job.run();

      verify(thirdPillarTransactionSynchronizer)
          .syncTransactions(eq(fixedNow.minusDays(2)), eq(fixedNow));
    }
  }
}
