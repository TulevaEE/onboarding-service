package ee.tuleva.onboarding.analytics.transaction.unitowner;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
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
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class ScheduledUnitOwnerSynchronizationJobTest {

  @Mock private UnitOwnerSynchronizer unitOwnerSynchronizer;
  @Mock private UnitOwnerRepository unitOwnerRepository;

  @InjectMocks private ScheduledUnitOwnerSynchronizationJob job;

  @Captor private ArgumentCaptor<LocalDate> snapshotDateCaptor;

  @BeforeEach
  void setUp() {
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

  private static LocalDate fixClockAfterPruningFloor() {
    ClockHolder.setClock(
        Clock.fixed(Instant.parse("2026-12-01T10:00:00Z"), ZoneId.of("Europe/Tallinn")));
    return LocalDate.now(ClockHolder.clock());
  }

  @Test
  void runDailySync_prunesOldSnapshots_keepingFirstOfMonthAndMondays() {
    LocalDate today = fixClockAfterPruningFloor();
    LocalDate oldPrunable = today.minusDays(60).with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
    LocalDate oldFirstOfMonth = today.minusDays(60).withDayOfMonth(1);
    LocalDate oldMonday = today.minusDays(60).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    LocalDate recent = today.minusDays(3);
    when(unitOwnerRepository.findDistinctSnapshotDates())
        .thenReturn(List.of(oldPrunable, oldFirstOfMonth, oldMonday, recent));

    job.runDailySync();

    verify(unitOwnerRepository).deleteBySnapshotDateIn(List.of(oldPrunable));
  }

  @Test
  void runDailySync_neverPrunesSnapshotsFromBeforeThePruningFloor() {
    fixClockAfterPruningFloor();
    LocalDate historicNonKeptWednesday = LocalDate.of(2026, 8, 19);
    when(unitOwnerRepository.findDistinctSnapshotDates())
        .thenReturn(List.of(historicNonKeptWednesday));

    job.runDailySync();

    verify(unitOwnerRepository, never()).deleteBySnapshotDateIn(any());
  }

  @Test
  void runDailySync_deletesNothing_whenAllOldSnapshotsAreKept() {
    LocalDate today = fixClockAfterPruningFloor();
    LocalDate oldFirstOfMonth = today.minusDays(60).withDayOfMonth(1);
    LocalDate recent = today.minusDays(3);
    when(unitOwnerRepository.findDistinctSnapshotDates())
        .thenReturn(List.of(oldFirstOfMonth, recent));

    job.runDailySync();

    verify(unitOwnerRepository, never()).deleteBySnapshotDateIn(any());
  }

  @Test
  void runDailySync_doesNotPrune_whenSynchronizationFails() {
    doThrow(new RuntimeException("Sync failed!")).when(unitOwnerSynchronizer).sync(any());

    job.runDailySync();

    verifyNoInteractions(unitOwnerRepository);
  }

  @Test
  void runDailySync_isScheduledDaily() throws NoSuchMethodException {
    Scheduled[] schedules =
        ScheduledUnitOwnerSynchronizationJob.class
            .getMethod("runDailySync")
            .getAnnotationsByType(Scheduled.class);

    assertThat(schedules).hasSize(1);
    assertThat(schedules[0].cron()).isEqualTo("0 30 4 * * *");
  }
}
