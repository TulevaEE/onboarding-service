package ee.tuleva.onboarding.analytics.transaction.thirdpillar;

import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.time.FixedClockConfig;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledThirdPillarTransactionSynchronizationJobTest extends FixedClockConfig {

  @Mock private ThirdPillarTransactionSynchronizer thirdPillarTransactionSynchronizer;
  @Mock private AnalyticsThirdPillarTransactionRepository transactionRepository;

  @InjectMocks private ScheduledThirdPillarTransactionSynchronizationJob job;

  private final LocalDate today = testLocalDateTime.toLocalDate();

  @Test
  void runDailySync_whenLatestDateExists_callsSynchronizerWithCorrectDates() {
    // given
    LocalDate latestDate = today.minusDays(1);
    when(transactionRepository.findLatestReportingDate()).thenReturn(Optional.of(latestDate));

    // when
    job.runDailySync();

    // then
    verify(transactionRepository).findLatestReportingDate();
    verify(thirdPillarTransactionSynchronizer).sync(eq(latestDate), eq(today));
    verifyNoMoreInteractions(thirdPillarTransactionSynchronizer, transactionRepository);
  }

  @Test
  void runDailySync_whenNoLatestDateExists_callsSynchronizerWithFallbackStartDate() {
    // given
    LocalDate fallbackStartDate = today.minusDays(2);
    when(transactionRepository.findLatestReportingDate()).thenReturn(Optional.empty());

    // when
    job.runDailySync();

    // then
    verify(transactionRepository).findLatestReportingDate();
    verify(thirdPillarTransactionSynchronizer).sync(eq(fallbackStartDate), eq(today));
    verifyNoMoreInteractions(thirdPillarTransactionSynchronizer, transactionRepository);
  }

  @Test
  void runDailySync_whenEndDateIsBeforeStartDate_doesNotCallSynchronizer() {
    // given
    LocalDate latestDate = today.plusDays(1);
    when(transactionRepository.findLatestReportingDate()).thenReturn(Optional.of(latestDate));

    // when
    job.runDailySync();

    // then
    verify(transactionRepository).findLatestReportingDate();
    verifyNoInteractions(thirdPillarTransactionSynchronizer);
    verifyNoMoreInteractions(transactionRepository);
  }

  @Test
  void runDailySync_whenSynchronizerThrowsException_logsError() {
    // given
    LocalDate latestDate = today.minusDays(1);
    when(transactionRepository.findLatestReportingDate()).thenReturn(Optional.of(latestDate));
    doThrow(new RuntimeException("Sync Failed!"))
        .when(thirdPillarTransactionSynchronizer)
        .sync(any(LocalDate.class), any(LocalDate.class));

    // when
    job.runDailySync();

    // then
    verify(transactionRepository).findLatestReportingDate();
    verify(thirdPillarTransactionSynchronizer).sync(eq(latestDate), eq(today));
    verifyNoMoreInteractions(thirdPillarTransactionSynchronizer, transactionRepository);
  }

  @Test
  void runInitialTransactionsSync_callsSynchronizerWithFixedDates() {
    // given
    LocalDate expectedStartDate = LocalDate.of(2025, 4, 1);
    LocalDate expectedEndDate = LocalDate.of(2025, 4, 8);

    // when
    job.runInitialTransactionsSync();

    // then
    verify(thirdPillarTransactionSynchronizer).sync(eq(expectedStartDate), eq(expectedEndDate));
    verifyNoInteractions(transactionRepository);
    verifyNoMoreInteractions(thirdPillarTransactionSynchronizer);
  }
}
