package ee.tuleva.onboarding.analytics.transaction.fund;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.time.FixedClockConfig;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  private static final int MAX_LOOKBACK_MONTHS = 6;
  private static final int DEFAULT_LOOKBACK_DAYS = 2;

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
    LocalDate fallbackStartDate = today.minusDays(DEFAULT_LOOKBACK_DAYS);
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
  void runDailySyncForThirdPillar_whenLatestDateIsOlderThanMaxLookback_capsStartDate() {
    LocalDate veryOldDate = today.minusMonths(MAX_LOOKBACK_MONTHS + 2);
    LocalDate expectedStartDate = today.minusMonths(MAX_LOOKBACK_MONTHS);
    when(transactionRepository.findLatestTransactionDateByIsin(thirdPillarIsin))
        .thenReturn(Optional.of(veryOldDate));

    job.runDailySyncForThirdPillar();

    verify(transactionRepository).findLatestTransactionDateByIsin(thirdPillarIsin);
    verify(fundTransactionSynchronizer).sync(eq(thirdPillarIsin), eq(expectedStartDate), eq(today));
    verifyNoMoreInteractions(fundTransactionSynchronizer, transactionRepository);
  }

  @Test
  void runDailySyncForThirdPillar_whenLatestDateIsExactlyMaxLookback_usesLatestDate() {
    LocalDate exactlyMaxLookbackDate = today.minusMonths(MAX_LOOKBACK_MONTHS);
    when(transactionRepository.findLatestTransactionDateByIsin(thirdPillarIsin))
        .thenReturn(Optional.of(exactlyMaxLookbackDate));

    job.runDailySyncForThirdPillar();

    verify(transactionRepository).findLatestTransactionDateByIsin(thirdPillarIsin);
    verify(fundTransactionSynchronizer)
        .sync(eq(thirdPillarIsin), eq(exactlyMaxLookbackDate), eq(today));
    verifyNoMoreInteractions(fundTransactionSynchronizer, transactionRepository);
  }

  @Test
  void runDailySyncForThirdPillar_whenLatestDateIsJustUnderMaxLookback_usesLatestDate() {
    LocalDate justUnderMaxLookbackDate = today.minusMonths(MAX_LOOKBACK_MONTHS).plusDays(1);
    when(transactionRepository.findLatestTransactionDateByIsin(thirdPillarIsin))
        .thenReturn(Optional.of(justUnderMaxLookbackDate));

    job.runDailySyncForThirdPillar();

    verify(transactionRepository).findLatestTransactionDateByIsin(thirdPillarIsin);
    verify(fundTransactionSynchronizer)
        .sync(eq(thirdPillarIsin), eq(justUnderMaxLookbackDate), eq(today));
    verifyNoMoreInteractions(fundTransactionSynchronizer, transactionRepository);
  }

  @Test
  void
      runDailySyncForThirdPillar_whenNoLatestDateAndDefaultLookbackIsWithinCap_usesDefaultLookback() {
    LocalDate expectedStartDate =
        today.minusDays(DEFAULT_LOOKBACK_DAYS); // DEFAULT_LOOKBACK_DAYS is 2
    when(transactionRepository.findLatestTransactionDateByIsin(thirdPillarIsin))
        .thenReturn(Optional.empty());

    job.runDailySyncForThirdPillar();

    verify(transactionRepository).findLatestTransactionDateByIsin(thirdPillarIsin);
    verify(fundTransactionSynchronizer).sync(eq(thirdPillarIsin), eq(expectedStartDate), eq(today));
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
    LocalDate fallbackStartDate = today.minusDays(DEFAULT_LOOKBACK_DAYS);
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
  void runDailySyncForSecondPillar_whenLatestDateIsOlderThanMaxLookback_capsStartDate() {
    LocalDate veryOldDate = today.minusMonths(MAX_LOOKBACK_MONTHS + 2);
    LocalDate expectedStartDate = today.minusMonths(MAX_LOOKBACK_MONTHS);
    when(transactionRepository.findLatestTransactionDateByIsin(secondPillarIsin))
        .thenReturn(Optional.of(veryOldDate));

    job.runDailySyncForSecondPillar();

    verify(transactionRepository).findLatestTransactionDateByIsin(secondPillarIsin);
    verify(fundTransactionSynchronizer)
        .sync(eq(secondPillarIsin), eq(expectedStartDate), eq(today));
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
    LocalDate fallbackStartDate = today.minusDays(DEFAULT_LOOKBACK_DAYS);
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
  void runDailySyncForSecondPillarBond_whenLatestDateIsOlderThanMaxLookback_capsStartDate() {
    LocalDate veryOldDate = today.minusMonths(MAX_LOOKBACK_MONTHS + 2);
    LocalDate expectedStartDate = today.minusMonths(MAX_LOOKBACK_MONTHS);
    when(transactionRepository.findLatestTransactionDateByIsin(secondPillarBondIsin))
        .thenReturn(Optional.of(veryOldDate));

    job.runDailySyncForSecondPillarBond();

    verify(transactionRepository).findLatestTransactionDateByIsin(secondPillarBondIsin);
    verify(fundTransactionSynchronizer)
        .sync(eq(secondPillarBondIsin), eq(expectedStartDate), eq(today));
    verifyNoMoreInteractions(fundTransactionSynchronizer, transactionRepository);
  }
}
