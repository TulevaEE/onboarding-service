package ee.tuleva.onboarding.analytics.thirdpillar.synchronization;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledThirdPillarTransactionSynchronizationJobTest {

  @Mock private ThirdPillarTransactionSynchronizer thirdPillarTransactionSynchronizer;
  @Mock private AnalyticsThirdPillarTransactionRepository transactionRepository;

  @InjectMocks private ScheduledThirdPillarTransactionSynchronizationJob job;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(TestClockHolder.clock);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void run_callsSyncTransactionsWithLatestDate_whenRepositoryHasData() {
    // given
    LocalDate fixedNowInTallinn = LocalDate.now(ClockHolder.clock());
    LocalDate latestDateInDb = fixedNowInTallinn.minusDays(1);

    when(transactionRepository.findLatestReportingDate()).thenReturn(Optional.of(latestDateInDb));

    // when
    job.run();

    // then
    verify(transactionRepository).findLatestReportingDate();
    verify(thirdPillarTransactionSynchronizer)
        .syncTransactions(eq(latestDateInDb), eq(fixedNowInTallinn));
    verifyNoMoreInteractions(thirdPillarTransactionSynchronizer, transactionRepository);
  }

  @Test
  void run_callsSyncTransactionsWithFallbackDate_whenRepositoryIsEmpty() {
    // given
    LocalDate fixedNowInTallinn = LocalDate.now(ClockHolder.clock());
    LocalDate fallbackStartDate = fixedNowInTallinn.minusDays(2);

    when(transactionRepository.findLatestReportingDate()).thenReturn(Optional.empty());

    // when
    job.run();

    // then
    verify(transactionRepository).findLatestReportingDate();
    verify(thirdPillarTransactionSynchronizer)
        .syncTransactions(eq(fallbackStartDate), eq(fixedNowInTallinn));
    verifyNoMoreInteractions(thirdPillarTransactionSynchronizer, transactionRepository);
  }

  @Test
  void run_doesNotCallSyncTransactions_whenEndDateIsBeforeStartDate() {
    // given
    LocalDate fixedNow = LocalDate.now(ClockHolder.clock());
    LocalDate latestDateInDb = fixedNow.plusDays(1);

    when(transactionRepository.findLatestReportingDate()).thenReturn(Optional.of(latestDateInDb));

    // when
    job.run();

    // then
    verify(transactionRepository).findLatestReportingDate();
    verifyNoInteractions(thirdPillarTransactionSynchronizer);
    verifyNoMoreInteractions(transactionRepository);
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
