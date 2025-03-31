package ee.tuleva.onboarding.analytics.thirdpillar.synchronization;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledThirdPillarTransactionSynchronizationJobTest {

  @Mock private ThirdPillarTransactionSynchronizer thirdPillarTransactionSynchronizer;

  @Mock private AnalyticsThirdPillarTransactionRepository transactionRepository; // Added mock

  @InjectMocks private ScheduledThirdPillarTransactionSynchronizationJob job;

  @Test
  void run_callsSyncTransactionsWithLatestDate_whenRepositoryHasData() {
    // given
    LocalDate fixedNow = LocalDate.of(2025, 3, 6);
    LocalDate latestDateInDb = LocalDate.of(2025, 3, 5);
    try (MockedStatic<LocalDate> localDateMock = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
      localDateMock.when(LocalDate::now).thenReturn(fixedNow);
      when(transactionRepository.findLatestReportingDate()).thenReturn(Optional.of(latestDateInDb));

      // when
      job.run();

      // then
      verify(transactionRepository).findLatestReportingDate();
      verify(thirdPillarTransactionSynchronizer).syncTransactions(eq(latestDateInDb), eq(fixedNow));
      verifyNoMoreInteractions(thirdPillarTransactionSynchronizer, transactionRepository);
    }
  }

  @Test
  void run_callsSyncTransactionsWithFallbackDate_whenRepositoryIsEmpty() {
    // given
    LocalDate fixedNow = LocalDate.of(2025, 3, 6);
    LocalDate fallbackStartDate = fixedNow.minusDays(2);
    try (MockedStatic<LocalDate> localDateMock = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
      localDateMock.when(LocalDate::now).thenReturn(fixedNow);
      when(transactionRepository.findLatestReportingDate()).thenReturn(Optional.empty());

      // when
      job.run();

      // then
      verify(transactionRepository).findLatestReportingDate();
      verify(thirdPillarTransactionSynchronizer)
          .syncTransactions(eq(fallbackStartDate), eq(fixedNow));
      verifyNoMoreInteractions(thirdPillarTransactionSynchronizer, transactionRepository);
    }
  }

  @Test
  void run_doesNotCallSyncTransactions_whenEndDateIsBeforeStartDate() {
    // given
    LocalDate fixedNow = LocalDate.of(2025, 3, 6);
    LocalDate latestDateInDb = LocalDate.of(2025, 3, 7); // Date after 'now'
    try (MockedStatic<LocalDate> localDateMock = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
      localDateMock.when(LocalDate::now).thenReturn(fixedNow);
      when(transactionRepository.findLatestReportingDate()).thenReturn(Optional.of(latestDateInDb));

      // when
      job.run();

      // then
      verify(transactionRepository).findLatestReportingDate();
      verifyNoInteractions(thirdPillarTransactionSynchronizer);
      verifyNoMoreInteractions(transactionRepository);
    }
  }

  @Test
  void runFebruaryTransactionsSync_callsSyncTransactionsWithFixedDates() {
    // given
    LocalDate expectedStartDate = LocalDate.of(2025, 2, 1);
    LocalDate expectedEndDate = LocalDate.of(2025, 2, 28);

    // when
    job.runFebruaryTransactionsSync();

    // then
    verify(thirdPillarTransactionSynchronizer)
        .syncTransactions(eq(expectedStartDate), eq(expectedEndDate));
    verifyNoInteractions(transactionRepository);
    verifyNoMoreInteractions(thirdPillarTransactionSynchronizer);
  }
}
