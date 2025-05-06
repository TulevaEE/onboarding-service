package ee.tuleva.onboarding.analytics.transaction.fund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.time.FixedClockConfig;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScheduledFundTransactionSynchronizationJobTest extends FixedClockConfig {

  @Mock private FundTransactionSynchronizer fundTransactionSynchronizer;
  @Mock private FundTransactionRepository transactionRepository;

  @InjectMocks private ScheduledFundTransactionSynchronizationJob job;

  private final String thirdPillarIsin = "EE3600001707";
  private final String secondPillarIsin = "EE3600109435";
  private final String secondPillarBondIsin = "EE3600109443";
  private final LocalDate today = testLocalDateTime.toLocalDate();

  @Captor private ArgumentCaptor<LocalDate> startDateCaptor;
  @Captor private ArgumentCaptor<LocalDate> endDateCaptor;
  @Captor private ArgumentCaptor<String> isinCaptor;

  @BeforeEach
  void setupIsin() {
    ReflectionTestUtils.setField(job, "thirdPillarIsin", thirdPillarIsin);
    ReflectionTestUtils.setField(job, "secondPillarIsin", secondPillarIsin);
    ReflectionTestUtils.setField(job, "secondPillarBondIsin", secondPillarBondIsin);
  }

  @Test
  void runDailySyncForThirdPillar_whenLatestDateExists_callsSynchronizerWithCorrectDates() {
    LocalDate latestDate = today.minusDays(1);
    when(transactionRepository.findLatestTransactionDateByIsin(thirdPillarIsin))
        .thenReturn(Optional.of(latestDate));

    job.runDailySyncForThirdPillar();

    verify(transactionRepository).findLatestTransactionDateByIsin(thirdPillarIsin);
    verify(fundTransactionSynchronizer).sync(eq(thirdPillarIsin), eq(latestDate), eq(today));
    verifyNoMoreInteractions(fundTransactionSynchronizer, transactionRepository);
  }

  @Test
  void runDailySyncForThirdPillar_whenNoLatestDateExists_callsSynchronizerWithFallbackStartDate() {
    LocalDate fallbackStartDate = today.minusDays(2);
    when(transactionRepository.findLatestTransactionDateByIsin(thirdPillarIsin))
        .thenReturn(Optional.empty());

    job.runDailySyncForThirdPillar();

    verify(transactionRepository).findLatestTransactionDateByIsin(thirdPillarIsin);
    verify(fundTransactionSynchronizer).sync(eq(thirdPillarIsin), eq(fallbackStartDate), eq(today));
    verifyNoMoreInteractions(fundTransactionSynchronizer, transactionRepository);
  }

  @Test
  void runDailySyncForThirdPillar_whenEndDateIsBeforeStartDate_doesNotCallSynchronizer() {
    LocalDate latestDate = today.plusDays(1);
    when(transactionRepository.findLatestTransactionDateByIsin(thirdPillarIsin))
        .thenReturn(Optional.of(latestDate));

    job.runDailySyncForThirdPillar();

    verify(transactionRepository).findLatestTransactionDateByIsin(thirdPillarIsin);
    verifyNoInteractions(fundTransactionSynchronizer);
    verifyNoMoreInteractions(transactionRepository);
  }

  @Test
  void runDailySyncForThirdPillar_whenSynchronizerThrowsException_logsError() {
    LocalDate latestDate = today.minusDays(1);
    when(transactionRepository.findLatestTransactionDateByIsin(thirdPillarIsin))
        .thenReturn(Optional.of(latestDate));
    doThrow(new RuntimeException("Sync Failed!"))
        .when(fundTransactionSynchronizer)
        .sync(eq(thirdPillarIsin), any(LocalDate.class), any(LocalDate.class));

    job.runDailySyncForThirdPillar();

    verify(transactionRepository).findLatestTransactionDateByIsin(thirdPillarIsin);
    verify(fundTransactionSynchronizer).sync(eq(thirdPillarIsin), eq(latestDate), eq(today));
    verifyNoMoreInteractions(fundTransactionSynchronizer, transactionRepository);
  }

  @Test
  void runDailySyncForSecondPillar_whenLatestDateExists_callsSynchronizerWithCorrectDates() {
    LocalDate latestDate = today.minusDays(1);
    when(transactionRepository.findLatestTransactionDateByIsin(secondPillarIsin))
        .thenReturn(Optional.of(latestDate));

    job.runDailySyncForSecondPillar();

    verify(transactionRepository).findLatestTransactionDateByIsin(secondPillarIsin);
    verify(fundTransactionSynchronizer).sync(eq(secondPillarIsin), eq(latestDate), eq(today));
    verifyNoMoreInteractions(fundTransactionSynchronizer, transactionRepository);
  }

  @Test
  void runDailySyncForSecondPillar_whenNoLatestDateExists_callsSynchronizerWithFallbackStartDate() {
    LocalDate fallbackStartDate = today.minusDays(2);
    when(transactionRepository.findLatestTransactionDateByIsin(secondPillarIsin))
        .thenReturn(Optional.empty());

    job.runDailySyncForSecondPillar();

    verify(transactionRepository).findLatestTransactionDateByIsin(secondPillarIsin);
    verify(fundTransactionSynchronizer)
        .sync(eq(secondPillarIsin), eq(fallbackStartDate), eq(today));
    verifyNoMoreInteractions(fundTransactionSynchronizer, transactionRepository);
  }

  @Test
  void runDailySyncForSecondPillar_whenEndDateIsBeforeStartDate_doesNotCallSynchronizer() {
    LocalDate latestDate = today.plusDays(1);
    when(transactionRepository.findLatestTransactionDateByIsin(secondPillarIsin))
        .thenReturn(Optional.of(latestDate));

    job.runDailySyncForSecondPillar();

    verify(transactionRepository).findLatestTransactionDateByIsin(secondPillarIsin);
    verify(fundTransactionSynchronizer, never()).sync(anyString(), any(), any());
    verifyNoMoreInteractions(transactionRepository);
  }

  @Test
  void runDailySyncForSecondPillar_whenSynchronizerThrowsException_logsError() {
    LocalDate latestDate = today.minusDays(1);
    when(transactionRepository.findLatestTransactionDateByIsin(secondPillarIsin))
        .thenReturn(Optional.of(latestDate));
    doThrow(new RuntimeException("Sync Failed!"))
        .when(fundTransactionSynchronizer)
        .sync(eq(secondPillarIsin), any(LocalDate.class), any(LocalDate.class));

    job.runDailySyncForSecondPillar();

    verify(transactionRepository).findLatestTransactionDateByIsin(secondPillarIsin);
    verify(fundTransactionSynchronizer).sync(eq(secondPillarIsin), eq(latestDate), eq(today));
    verifyNoMoreInteractions(fundTransactionSynchronizer, transactionRepository);
  }

  @Test
  void runDailySyncForSecondPillarBond_whenLatestDateExists_callsSynchronizerWithCorrectDates() {
    LocalDate latestDate = today.minusDays(1);
    when(transactionRepository.findLatestTransactionDateByIsin(secondPillarBondIsin))
        .thenReturn(Optional.of(latestDate));

    job.runDailySyncForSecondPillarBond();

    verify(transactionRepository).findLatestTransactionDateByIsin(secondPillarBondIsin);
    verify(fundTransactionSynchronizer).sync(eq(secondPillarBondIsin), eq(latestDate), eq(today));
    verifyNoMoreInteractions(fundTransactionSynchronizer, transactionRepository);
  }

  @Test
  void
      runDailySyncForSecondPillarBond_whenNoLatestDateExists_callsSynchronizerWithFallbackStartDate() {
    LocalDate fallbackStartDate = today.minusDays(2);
    when(transactionRepository.findLatestTransactionDateByIsin(secondPillarBondIsin))
        .thenReturn(Optional.empty());

    job.runDailySyncForSecondPillarBond();

    verify(transactionRepository).findLatestTransactionDateByIsin(secondPillarBondIsin);
    verify(fundTransactionSynchronizer)
        .sync(eq(secondPillarBondIsin), eq(fallbackStartDate), eq(today));
    verifyNoMoreInteractions(fundTransactionSynchronizer, transactionRepository);
  }

  @Test
  void runDailySyncForSecondPillarBond_whenEndDateIsBeforeStartDate_doesNotCallSynchronizer() {
    LocalDate latestDate = today.plusDays(1);
    when(transactionRepository.findLatestTransactionDateByIsin(secondPillarBondIsin))
        .thenReturn(Optional.of(latestDate));

    job.runDailySyncForSecondPillarBond();

    verify(transactionRepository).findLatestTransactionDateByIsin(secondPillarBondIsin);
    verify(fundTransactionSynchronizer, never()).sync(anyString(), any(), any());
    verifyNoMoreInteractions(transactionRepository);
  }

  @Test
  void runDailySyncForSecondPillarBond_whenSynchronizerThrowsException_logsError() {
    LocalDate latestDate = today.minusDays(1);
    when(transactionRepository.findLatestTransactionDateByIsin(secondPillarBondIsin))
        .thenReturn(Optional.of(latestDate));
    doThrow(new RuntimeException("Sync Failed!"))
        .when(fundTransactionSynchronizer)
        .sync(eq(secondPillarBondIsin), any(LocalDate.class), any(LocalDate.class));

    job.runDailySyncForSecondPillarBond();

    verify(transactionRepository).findLatestTransactionDateByIsin(secondPillarBondIsin);
    verify(fundTransactionSynchronizer).sync(eq(secondPillarBondIsin), eq(latestDate), eq(today));
    verifyNoMoreInteractions(fundTransactionSynchronizer, transactionRepository);
  }

  @Test
  void
      runInitialTransactionsSync_callsSynchronizerWithFixedStartDateAndCurrentEndDateForSecondPillar() {
    LocalDate expectedStartDate = LocalDate.of(2025, 2, 1);
    LocalDate expectedEndDate = today;

    job.runInitialTransactionsSync();

    verify(fundTransactionSynchronizer)
        .sync(eq(secondPillarIsin), eq(expectedStartDate), eq(expectedEndDate));
    verifyNoInteractions(transactionRepository);
    verifyNoMoreInteractions(fundTransactionSynchronizer);
  }

  @Test
  void runInitialTransactionsSync_whenSynchronizerThrowsException_logsError() {
    LocalDate expectedStartDate = LocalDate.of(2025, 2, 1);
    LocalDate expectedEndDate = today;
    doThrow(new RuntimeException("Initial Sync Failed!"))
        .when(fundTransactionSynchronizer)
        .sync(eq(secondPillarIsin), eq(expectedStartDate), eq(expectedEndDate));

    job.runInitialTransactionsSync();

    verify(fundTransactionSynchronizer)
        .sync(eq(secondPillarIsin), eq(expectedStartDate), eq(expectedEndDate));
    verifyNoInteractions(transactionRepository);
    verifyNoMoreInteractions(fundTransactionSynchronizer);
  }

  private long calculateExpectedBatches(LocalDate start, LocalDate end) {
    if (start.isAfter(end)) {
      return 0;
    }
    return ChronoUnit.MONTHS.between(start.withDayOfMonth(1), end.withDayOfMonth(1)) + 1;
  }

  @Test
  void runHistoricalSync_callsSynchronizerInMonthlyBatchesForEachIsin() {
    LocalDate secondPillarOverallStart = LocalDate.of(2017, Month.MARCH, 28);
    LocalDate secondPillarOverallEnd = LocalDate.of(2025, Month.FEBRUARY, 3);
    LocalDate secondPillarBondOverallStart = LocalDate.of(2017, Month.MARCH, 28);
    LocalDate secondPillarBondOverallEnd = LocalDate.of(2025, Month.APRIL, 29);
    LocalDate thirdPillarOverallStart = LocalDate.of(2019, Month.OCTOBER, 14);
    LocalDate thirdPillarOverallEnd = LocalDate.of(2025, Month.FEBRUARY, 3);

    long expectedSecondPillarBatches =
        calculateExpectedBatches(secondPillarOverallStart, secondPillarOverallEnd);
    long expectedSecondPillarBondBatches =
        calculateExpectedBatches(secondPillarBondOverallStart, secondPillarBondOverallEnd);
    long expectedThirdPillarBatches =
        calculateExpectedBatches(thirdPillarOverallStart, thirdPillarOverallEnd);
    long totalExpectedBatches =
        expectedSecondPillarBatches + expectedSecondPillarBondBatches + expectedThirdPillarBatches;

    job.runHistoricalSync();

    verify(fundTransactionSynchronizer, times((int) totalExpectedBatches))
        .sync(isinCaptor.capture(), startDateCaptor.capture(), endDateCaptor.capture());

    List<String> capturedIsins = isinCaptor.getAllValues();
    List<LocalDate> capturedStartDates = startDateCaptor.getAllValues();
    List<LocalDate> capturedEndDates = endDateCaptor.getAllValues();

    int secondPillarCount = 0;
    LocalDate firstSecondPillarStart = null;
    LocalDate lastSecondPillarEnd = null;
    int secondPillarBondCount = 0;
    LocalDate firstSecondPillarBondStart = null;
    LocalDate lastSecondPillarBondEnd = null;
    int thirdPillarCount = 0;
    LocalDate firstThirdPillarStart = null;
    LocalDate lastThirdPillarEnd = null;

    for (int i = 0; i < capturedIsins.size(); i++) {
      String isin = capturedIsins.get(i);
      LocalDate start = capturedStartDates.get(i);
      LocalDate end = capturedEndDates.get(i);

      if (isin.equals(secondPillarIsin)) {
        if (firstSecondPillarStart == null) firstSecondPillarStart = start;
        lastSecondPillarEnd = end;
        secondPillarCount++;
      } else if (isin.equals(secondPillarBondIsin)) {
        if (firstSecondPillarBondStart == null) firstSecondPillarBondStart = start;
        lastSecondPillarBondEnd = end;
        secondPillarBondCount++;
      } else if (isin.equals(thirdPillarIsin)) {
        if (firstThirdPillarStart == null) firstThirdPillarStart = start;
        lastThirdPillarEnd = end;
        thirdPillarCount++;
      }
      LocalDate expectedEndOfMonth = start.with(TemporalAdjusters.lastDayOfMonth());
      LocalDate overallEndDate =
          isin.equals(secondPillarIsin)
              ? secondPillarOverallEnd
              : isin.equals(secondPillarBondIsin)
                  ? secondPillarBondOverallEnd
                  : thirdPillarOverallEnd;
      LocalDate expectedEndDate =
          expectedEndOfMonth.isAfter(overallEndDate) ? overallEndDate : expectedEndOfMonth;
      assertEquals(
          expectedEndDate,
          end,
          "End date for batch starting " + start + " for ISIN " + isin + " is incorrect.");
    }

    assertEquals(expectedSecondPillarBatches, secondPillarCount);
    assertEquals(secondPillarOverallStart, firstSecondPillarStart);
    assertEquals(secondPillarOverallEnd, lastSecondPillarEnd);

    assertEquals(expectedSecondPillarBondBatches, secondPillarBondCount);
    assertEquals(secondPillarBondOverallStart, firstSecondPillarBondStart);
    assertEquals(secondPillarBondOverallEnd, lastSecondPillarBondEnd);

    assertEquals(expectedThirdPillarBatches, thirdPillarCount);
    assertEquals(thirdPillarOverallStart, firstThirdPillarStart);
    assertEquals(thirdPillarOverallEnd, lastThirdPillarEnd);

    verifyNoMoreInteractions(fundTransactionSynchronizer);
    verifyNoInteractions(transactionRepository);
  }

  @Test
  void runHistoricalSync_whenOneBatchFails_continuesWithSubsequentBatchesAndOtherIsins() {
    LocalDate secondPillarOverallStart = LocalDate.of(2017, Month.MARCH, 28);
    LocalDate secondPillarOverallEnd = LocalDate.of(2025, Month.FEBRUARY, 3);
    LocalDate secondPillarBondOverallStart = LocalDate.of(2017, Month.MARCH, 28);
    LocalDate secondPillarBondOverallEnd = LocalDate.of(2025, Month.APRIL, 29);
    LocalDate thirdPillarOverallStart = LocalDate.of(2019, Month.OCTOBER, 14);
    LocalDate thirdPillarOverallEnd = LocalDate.of(2025, Month.FEBRUARY, 3);

    long expectedSecondPillarBatches =
        calculateExpectedBatches(secondPillarOverallStart, secondPillarOverallEnd);
    long expectedSecondPillarBondBatches =
        calculateExpectedBatches(secondPillarBondOverallStart, secondPillarBondOverallEnd);
    long expectedThirdPillarBatches =
        calculateExpectedBatches(thirdPillarOverallStart, thirdPillarOverallEnd);
    long totalExpectedBatches =
        expectedSecondPillarBatches + expectedSecondPillarBondBatches + expectedThirdPillarBatches;

    LocalDate firstBatchStart = secondPillarOverallStart;
    LocalDate firstBatchEnd =
        firstBatchStart.with(TemporalAdjusters.lastDayOfMonth()); // 2017-03-31

    doThrow(new RuntimeException("Batch Sync Failed!"))
        .when(fundTransactionSynchronizer)
        .sync(eq(secondPillarIsin), eq(firstBatchStart), eq(firstBatchEnd));

    job.runHistoricalSync();

    verify(fundTransactionSynchronizer, times((int) totalExpectedBatches))
        .sync(anyString(), any(LocalDate.class), any(LocalDate.class));

    verify(fundTransactionSynchronizer)
        .sync(eq(secondPillarIsin), eq(firstBatchStart), eq(firstBatchEnd));

    LocalDate nextBatchStart = firstBatchEnd.plusDays(1); // 2017-04-01
    LocalDate nextBatchEnd = nextBatchStart.with(TemporalAdjusters.lastDayOfMonth()); // 2017-04-30
    verify(fundTransactionSynchronizer)
        .sync(eq(secondPillarIsin), eq(nextBatchStart), eq(nextBatchEnd));

    LocalDate firstBondBatchStart = secondPillarBondOverallStart;
    LocalDate firstBondBatchEnd = firstBondBatchStart.with(TemporalAdjusters.lastDayOfMonth());
    verify(fundTransactionSynchronizer)
        .sync(eq(secondPillarBondIsin), eq(firstBondBatchStart), eq(firstBondBatchEnd));

    LocalDate firstThirdPillarBatchStart = thirdPillarOverallStart;
    LocalDate firstThirdPillarBatchEnd =
        firstThirdPillarBatchStart.with(TemporalAdjusters.lastDayOfMonth());
    verify(fundTransactionSynchronizer)
        .sync(eq(thirdPillarIsin), eq(firstThirdPillarBatchStart), eq(firstThirdPillarBatchEnd));

    verifyNoMoreInteractions(fundTransactionSynchronizer);
    verifyNoInteractions(transactionRepository);
  }
}
