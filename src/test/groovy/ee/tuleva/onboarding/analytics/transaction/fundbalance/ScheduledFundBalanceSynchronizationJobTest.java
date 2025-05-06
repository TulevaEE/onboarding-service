package ee.tuleva.onboarding.analytics.transaction.fundbalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.time.FixedClockConfig;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledFundBalanceSynchronizationJobTest extends FixedClockConfig {

  @Mock private FundBalanceSynchronizer fundBalanceSynchronizer;

  @InjectMocks private ScheduledFundBalanceSynchronizationJob scheduledJob;

  @Captor private ArgumentCaptor<LocalDate> dateCaptor;

  private final LocalDate today = testLocalDateTime.toLocalDate();
  private final LocalDate yesterday = today.minusDays(1);

  @Test
  @DisplayName("runDailySync calls synchronizer with yesterday's date on success")
  void runDailySync_callsSynchronizerWithCorrectDate_onSuccess() {

    // Act
    scheduledJob.runDailySync();

    // Assert
    verify(fundBalanceSynchronizer).sync(dateCaptor.capture());
    LocalDate actualSyncDate = dateCaptor.getValue();

    assertThat(actualSyncDate).isEqualTo(yesterday);

    verifyNoMoreInteractions(fundBalanceSynchronizer);
  }

  @Test
  @DisplayName("runDailySync catches exceptions from synchronizer and does not throw")
  void runDailySync_catchesAndLogsError_whenSyncFails() {
    // Arrange
    RuntimeException simulatedException = new RuntimeException("Sync failed!");
    doThrow(simulatedException).when(fundBalanceSynchronizer).sync(eq(yesterday));

    // Act & Assert
    assertDoesNotThrow(
        () -> {
          scheduledJob.runDailySync();
        },
        "Scheduled job should catch the exception from the synchronizer.");

    verify(fundBalanceSynchronizer).sync(eq(yesterday));
    verifyNoMoreInteractions(fundBalanceSynchronizer);
  }

  @Test
  @DisplayName("runInitialFundBalanceSync calls synchronizer with yesterday's date on success")
  void runInitialFundBalanceSync_callsSynchronizerWithCorrectDate_onSuccess() {
    // Act
    scheduledJob.runInitialFundBalanceSync();

    // Assert
    verify(fundBalanceSynchronizer).sync(dateCaptor.capture());
    LocalDate actualSyncDate = dateCaptor.getValue();

    assertThat(actualSyncDate).isEqualTo(yesterday);

    verifyNoMoreInteractions(fundBalanceSynchronizer);
  }

  @Test
  @DisplayName("runInitialFundBalanceSync catches exceptions from synchronizer and does not throw")
  void runInitialFundBalanceSync_catchesAndLogsError_whenSyncFails() {
    // Arrange
    RuntimeException simulatedException = new RuntimeException("Initial sync failed!");
    doThrow(simulatedException).when(fundBalanceSynchronizer).sync(eq(yesterday));

    // Act & Assert
    assertDoesNotThrow(
        () -> {
          scheduledJob.runInitialFundBalanceSync();
        },
        "Initial scheduled job should catch the exception from the synchronizer.");

    verify(fundBalanceSynchronizer).sync(eq(yesterday));
    verifyNoMoreInteractions(fundBalanceSynchronizer);
  }

  @Test
  @DisplayName(
      "runHistoricalFundBalanceSync calls synchronizer for each day in the specified range")
  void runHistoricalFundBalanceSync_callsSynchronizerForEachDayInRange() {
    LocalDate startDate = LocalDate.of(2017, Month.MARCH, 28);
    LocalDate endDate = LocalDate.of(2025, Month.APRIL, 21);
    long expectedDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

    scheduledJob.runHistoricalFundBalanceSync();

    verify(fundBalanceSynchronizer, times((int) expectedDays)).sync(dateCaptor.capture());

    List<LocalDate> capturedDates = dateCaptor.getAllValues();
    assertThat(capturedDates.size()).isEqualTo(expectedDays);
    assertThat(capturedDates.get(0)).isEqualTo(startDate);
    assertThat(capturedDates.get(capturedDates.size() - 1)).isEqualTo(endDate);

    verifyNoMoreInteractions(fundBalanceSynchronizer);
  }

  @Test
  @DisplayName("runHistoricalFundBalanceSync continues processing even if one day's sync fails")
  void runHistoricalFundBalanceSync_whenOneDayFails_continuesWithNextDays() {
    LocalDate startDate = LocalDate.of(2017, Month.MARCH, 28);
    LocalDate endDate = LocalDate.of(2025, Month.APRIL, 21);
    LocalDate failingDate = startDate.plusDays(1); // 2017-03-29
    long expectedDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

    RuntimeException simulatedException = new RuntimeException("Sync failed for specific date!");
    doThrow(simulatedException).when(fundBalanceSynchronizer).sync(eq(failingDate));

    assertDoesNotThrow(
        () -> scheduledJob.runHistoricalFundBalanceSync(),
        "Historical job should catch the exception and continue.");

    verify(fundBalanceSynchronizer, times((int) expectedDays)).sync(any(LocalDate.class));

    verify(fundBalanceSynchronizer).sync(eq(failingDate));

    verify(fundBalanceSynchronizer).sync(eq(startDate));
    verify(fundBalanceSynchronizer).sync(eq(failingDate.plusDays(1)));
    verify(fundBalanceSynchronizer).sync(eq(endDate));

    verifyNoMoreInteractions(fundBalanceSynchronizer);
  }
}
