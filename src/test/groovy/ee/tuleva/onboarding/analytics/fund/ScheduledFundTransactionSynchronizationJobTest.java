package ee.tuleva.onboarding.analytics.fund;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
class ScheduledFundTransactionSynchronizationJobTest {

  @Mock private FundTransactionSynchronizer fundTransactionSynchronizer;

  @Mock private AnalyticsFundTransactionRepository transactionRepository;

  @InjectMocks private ScheduledFundTransactionSynchronizationJob job;

  private final String expectedFundIsin = "EE3600001707";
  private static final int DEFAULT_LOOKBACK_DAYS = 2;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(TestClockHolder.clock);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void runDailySync_whenRepositoryHasData_callsSyncWithCorrectDates() {
    // given
    LocalDate endDate = LocalDate.now(ClockHolder.clock());
    LocalDate latestDateInDb = endDate.minusDays(1);
    when(transactionRepository.findLatestTransactionDate()).thenReturn(Optional.of(latestDateInDb));

    // when
    job.runDailySync();

    // then
    verify(transactionRepository).findLatestTransactionDate();
    verify(fundTransactionSynchronizer)
        .syncTransactions(eq(expectedFundIsin), eq(latestDateInDb), eq(endDate));
    verifyNoMoreInteractions(fundTransactionSynchronizer, transactionRepository);
  }

  @Test
  void runDailySync_whenRepositoryIsEmpty_callsSyncWithFallbackDates() {
    // given
    LocalDate endDate = LocalDate.now(ClockHolder.clock());
    LocalDate expectedStartDate = endDate.minusDays(DEFAULT_LOOKBACK_DAYS);
    when(transactionRepository.findLatestTransactionDate()).thenReturn(Optional.empty());

    // when
    job.runDailySync();

    // then
    verify(transactionRepository).findLatestTransactionDate();
    verify(fundTransactionSynchronizer)
        .syncTransactions(eq(expectedFundIsin), eq(expectedStartDate), eq(endDate));
    verifyNoMoreInteractions(fundTransactionSynchronizer, transactionRepository);
  }

  @Test
  void runDailySync_whenEndDateIsBeforeStartDate_skipsSync() {
    // given
    LocalDate endDate = LocalDate.now(ClockHolder.clock());
    LocalDate latestDateInDb = endDate.plusDays(1);
    when(transactionRepository.findLatestTransactionDate()).thenReturn(Optional.of(latestDateInDb));

    // when
    job.runDailySync();

    // then
    verify(transactionRepository).findLatestTransactionDate();
    verifyNoInteractions(fundTransactionSynchronizer);
    verifyNoMoreInteractions(transactionRepository);
  }

  @Test
  void runDailySync_whenSynchronizerThrowsException_logsErrorAndDoesNotPropagate() {
    // given
    LocalDate endDate = LocalDate.now(ClockHolder.clock());
    LocalDate latestDateInDb = endDate.minusDays(1);
    when(transactionRepository.findLatestTransactionDate()).thenReturn(Optional.of(latestDateInDb));
    doThrow(new RuntimeException("Database connection error"))
        .when(fundTransactionSynchronizer)
        .syncTransactions(anyString(), any(LocalDate.class), any(LocalDate.class));

    // when
    job.runDailySync();

    // then
    verify(transactionRepository).findLatestTransactionDate();
    verify(fundTransactionSynchronizer)
        .syncTransactions(eq(expectedFundIsin), eq(latestDateInDb), eq(endDate));
    verifyNoMoreInteractions(fundTransactionSynchronizer, transactionRepository);
  }

  @Test
  void runInitialTransactionsSync_callsSyncWithFixedDates() {
    // given
    LocalDate expectedStartDate = LocalDate.of(2025, 2, 1);
    LocalDate expectedEndDate = LocalDate.now(ClockHolder.clock());

    // when
    job.runInitialTransactionsSync();

    // then
    verify(fundTransactionSynchronizer)
        .syncTransactions(eq(expectedFundIsin), eq(expectedStartDate), eq(expectedEndDate));
    verifyNoInteractions(transactionRepository);
    verifyNoMoreInteractions(fundTransactionSynchronizer);
  }
}
