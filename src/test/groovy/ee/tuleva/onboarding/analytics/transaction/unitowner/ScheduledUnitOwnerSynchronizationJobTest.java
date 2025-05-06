package ee.tuleva.onboarding.analytics.transaction.unitowner;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledUnitOwnerSynchronizationJobTest {

  @Mock private UnitOwnerSynchronizer unitOwnerSynchronizer;

  @InjectMocks private ScheduledUnitOwnerSynchronizationJob job;

  @Captor private ArgumentCaptor<LocalDate> snapshotDateCaptor;

  @BeforeEach
  void setUp() {
    // given
    ClockHolder.setClock(TestClockHolder.clock);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void runDailySync_callsSynchronizerWithCorrectDateFromClockHolder() {
    // given
    LocalDate expectedSnapshotDate = LocalDate.now(TestClockHolder.clock);

    // when
    job.runDailySync();

    // then
    verify(unitOwnerSynchronizer).sync(snapshotDateCaptor.capture());
    LocalDate actualSnapshotDate = snapshotDateCaptor.getValue();
    assertThat(actualSnapshotDate).isEqualTo(expectedSnapshotDate);
    verifyNoMoreInteractions(unitOwnerSynchronizer);
  }

  @Test
  void runDailySync_logsErrorAndCompletes_whenSynchronizerThrowsException() {
    // given
    LocalDate expectedSnapshotDate = LocalDate.now(TestClockHolder.clock);
    RuntimeException simulatedException = new RuntimeException("Sync failed!");
    doThrow(simulatedException).when(unitOwnerSynchronizer).sync(any(LocalDate.class));

    // when
    assertDoesNotThrow(
        () -> {
          job.runDailySync();
        },
        "Daily sync job should catch exceptions and complete.");

    // then
    verify(unitOwnerSynchronizer).sync(snapshotDateCaptor.capture());
    LocalDate actualSnapshotDate = snapshotDateCaptor.getValue();
    assertThat(actualSnapshotDate).isEqualTo(expectedSnapshotDate);

    verifyNoMoreInteractions(unitOwnerSynchronizer);
  }

  @Test
  void runMonthlySync_callsSynchronizerWithCorrectDateFromClockHolder() {
    LocalDate expectedSnapshotDate = LocalDate.now(TestClockHolder.clock);

    job.runMonthlySync();

    verify(unitOwnerSynchronizer).sync(snapshotDateCaptor.capture());
    LocalDate actualSnapshotDate = snapshotDateCaptor.getValue();
    assertThat(actualSnapshotDate).isEqualTo(expectedSnapshotDate);
    verifyNoMoreInteractions(unitOwnerSynchronizer);
  }

  @Test
  void runMonthlySync_logsErrorAndCompletes_whenSynchronizerThrowsException() {
    LocalDate expectedSnapshotDate = LocalDate.now(TestClockHolder.clock);
    RuntimeException simulatedException = new RuntimeException("Monthly sync failed!");
    doThrow(simulatedException).when(unitOwnerSynchronizer).sync(any(LocalDate.class));

    assertDoesNotThrow(
        () -> {
          job.runMonthlySync();
        },
        "Monthly sync job should catch exceptions and complete.");

    verify(unitOwnerSynchronizer).sync(snapshotDateCaptor.capture());
    LocalDate actualSnapshotDate = snapshotDateCaptor.getValue();
    assertThat(actualSnapshotDate).isEqualTo(expectedSnapshotDate);

    verifyNoMoreInteractions(unitOwnerSynchronizer);
  }

  @Test
  void runInitialUnitOwnerSync_callsSynchronizerWithCorrectDateFromClockHolder() {
    // given
    LocalDate expectedSnapshotDate = LocalDate.now(TestClockHolder.clock);

    // when
    job.runInitialUnitOwnerSync();

    // then
    verify(unitOwnerSynchronizer).sync(snapshotDateCaptor.capture());
    LocalDate actualSnapshotDate = snapshotDateCaptor.getValue();
    assertThat(actualSnapshotDate).isEqualTo(expectedSnapshotDate);
    verifyNoMoreInteractions(unitOwnerSynchronizer);
  }

  @Test
  void runInitialUnitOwnerSync_logsErrorAndCompletes_whenSynchronizerThrowsException() {
    // given
    LocalDate expectedSnapshotDate = LocalDate.now(TestClockHolder.clock);
    RuntimeException simulatedException = new RuntimeException("Initial sync failed!");
    doThrow(simulatedException).when(unitOwnerSynchronizer).sync(any(LocalDate.class));

    // when
    assertDoesNotThrow(
        () -> {
          job.runInitialUnitOwnerSync();
        },
        "Initial sync job should catch exceptions and complete.");

    // then
    verify(unitOwnerSynchronizer).sync(snapshotDateCaptor.capture());
    LocalDate actualSnapshotDate = snapshotDateCaptor.getValue();
    assertThat(actualSnapshotDate).isEqualTo(expectedSnapshotDate);

    verifyNoMoreInteractions(unitOwnerSynchronizer);
  }

  @Test
  void runHistoricalUnitOwnerSync_callsSynchronizerForEachMonthStartInRange() {
    LocalDate overallStartDate = LocalDate.of(2017, Month.MARCH, 28);
    LocalDate overallEndDate = LocalDate.of(2025, Month.APRIL, 1);

    LocalDate firstSyncDate = overallStartDate.withDayOfMonth(1); // 2017-03-01
    LocalDate lastSyncDate = overallEndDate.withDayOfMonth(1); // 2025-04-01

    long expectedMonths = ChronoUnit.MONTHS.between(firstSyncDate, lastSyncDate) + 1;

    job.runHistoricalUnitOwnerSync();

    verify(unitOwnerSynchronizer, times((int) expectedMonths)).sync(snapshotDateCaptor.capture());

    List<LocalDate> capturedDates = snapshotDateCaptor.getAllValues();
    assertThat(capturedDates.size()).isEqualTo(expectedMonths);
    assertThat(capturedDates.get(0)).isEqualTo(firstSyncDate);
    assertThat(capturedDates.get(capturedDates.size() - 1)).isEqualTo(lastSyncDate);

    for (int i = 0; i < expectedMonths; i++) {
      assertThat(capturedDates.get(i).getDayOfMonth()).isEqualTo(1);
      if (i > 0) {
        assertThat(capturedDates.get(i)).isEqualTo(capturedDates.get(i - 1).plusMonths(1));
      }
    }

    verifyNoMoreInteractions(unitOwnerSynchronizer);
  }

  @Test
  void runHistoricalUnitOwnerSync_continuesWhenOneMonthSyncFails() {
    LocalDate overallStartDate = LocalDate.of(2017, Month.MARCH, 28);
    LocalDate overallEndDate = LocalDate.of(2025, Month.APRIL, 1);

    LocalDate firstSyncDate = overallStartDate.withDayOfMonth(1); // 2017-03-01
    LocalDate failingSyncDate = firstSyncDate.plusMonths(1); // 2017-04-01
    LocalDate lastSyncDate = overallEndDate.withDayOfMonth(1); // 2025-04-01

    long expectedMonths = ChronoUnit.MONTHS.between(firstSyncDate, lastSyncDate) + 1;

    RuntimeException simulatedException = new RuntimeException("Sync failed for specific month!");
    doThrow(simulatedException).when(unitOwnerSynchronizer).sync(eq(failingSyncDate));

    assertDoesNotThrow(
        () -> job.runHistoricalUnitOwnerSync(),
        "Historical job should catch the exception and continue.");

    verify(unitOwnerSynchronizer, times((int) expectedMonths)).sync(any(LocalDate.class));

    verify(unitOwnerSynchronizer).sync(eq(failingSyncDate));

    verify(unitOwnerSynchronizer).sync(eq(firstSyncDate));
    verify(unitOwnerSynchronizer).sync(eq(failingSyncDate.plusMonths(1))); // 2017-05-01
    verify(unitOwnerSynchronizer).sync(eq(lastSyncDate));

    verifyNoMoreInteractions(unitOwnerSynchronizer);
  }
}
