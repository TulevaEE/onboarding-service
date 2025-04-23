package ee.tuleva.onboarding.analytics.transaction.fundbalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.time.FixedClockConfig;
import java.time.LocalDate;
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

  @Test
  @DisplayName("runDailySync calls synchronizer with the current date on success")
  void runDailySync_callsSynchronizerWithCorrectDate_onSuccess() {

    // Act
    scheduledJob.runDailySync();

    // Assert
    verify(fundBalanceSynchronizer).sync(dateCaptor.capture());
    LocalDate actualSyncDate = dateCaptor.getValue();

    assertThat(actualSyncDate).isEqualTo(today);

    verifyNoMoreInteractions(fundBalanceSynchronizer);
  }

  @Test
  @DisplayName("runDailySync catches exceptions from synchronizer and does not throw")
  void runDailySync_catchesAndLogsError_whenSyncFails() {
    // Arrange
    RuntimeException simulatedException = new RuntimeException("Sync failed!");
    doThrow(simulatedException).when(fundBalanceSynchronizer).sync(eq(today));

    // Act & Assert
    assertDoesNotThrow(
        () -> {
          scheduledJob.runDailySync();
        },
        "Scheduled job should catch the exception from the synchronizer.");

    verify(fundBalanceSynchronizer).sync(eq(today));
    verifyNoMoreInteractions(fundBalanceSynchronizer);
  }

  @Test
  @DisplayName("runInitialFundBalanceSync calls synchronizer with the current date on success")
  void runInitialFundBalanceSync_callsSynchronizerWithCorrectDate_onSuccess() {
    // Act
    scheduledJob.runInitialFundBalanceSync();

    // Assert
    verify(fundBalanceSynchronizer).sync(dateCaptor.capture());
    LocalDate actualSyncDate = dateCaptor.getValue();

    assertThat(actualSyncDate).isEqualTo(today);

    verifyNoMoreInteractions(fundBalanceSynchronizer);
  }

  @Test
  @DisplayName("runInitialFundBalanceSync catches exceptions from synchronizer and does not throw")
  void runInitialFundBalanceSync_catchesAndLogsError_whenSyncFails() {
    // Arrange
    RuntimeException simulatedException = new RuntimeException("Initial sync failed!");
    doThrow(simulatedException).when(fundBalanceSynchronizer).sync(eq(today));

    // Act & Assert
    assertDoesNotThrow(
        () -> {
          scheduledJob.runInitialFundBalanceSync();
        },
        "Initial scheduled job should catch the exception from the synchronizer.");

    verify(fundBalanceSynchronizer).sync(eq(today));
    verifyNoMoreInteractions(fundBalanceSynchronizer);
  }
}
