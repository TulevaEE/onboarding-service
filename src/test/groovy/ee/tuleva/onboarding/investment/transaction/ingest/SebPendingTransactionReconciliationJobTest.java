package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.pipeline.PipelineStep.EXECUTION_MATCHING;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.pipeline.PipelineNotifier;
import ee.tuleva.onboarding.pipeline.PipelineRun;
import ee.tuleva.onboarding.pipeline.PipelineTracker;
import ee.tuleva.onboarding.time.MutableClock;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebPendingTransactionReconciliationJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 5, 18);
  private static final LocalDate SATURDAY = LocalDate.of(2026, 5, 23);

  @Spy private Clock clock = Clock.fixed(TODAY.atStartOfDay(TALLINN).toInstant(), TALLINN);

  @Mock private PublicHolidays publicHolidays;
  @Mock private InvestmentReportService reportService;
  @Mock private SebPendingTransactionReconciliationService reconciliationService;
  @Mock private PipelineTracker pipelineTracker;
  @Mock private PipelineNotifier pipelineNotifier;

  private SebPendingTransactionReconciliationJob job;

  @BeforeEach
  void freeReconciliationLock() {
    job = jobLockedBy(RecordingLockProvider.free());
  }

  @Test
  void run_skipsOnNonWorkingDay() {
    Clock saturdayClock = Clock.fixed(SATURDAY.atStartOfDay(TALLINN).toInstant(), TALLINN);
    given(clock.instant()).willReturn(saturdayClock.instant());
    given(clock.getZone()).willReturn(saturdayClock.getZone());
    given(publicHolidays.isWorkingDay(SATURDAY)).willReturn(false);

    job.runOnSchedule();

    verifyNoInteractions(reportService, reconciliationService, pipelineTracker, pipelineNotifier);
  }

  @Test
  void run_tracksExecutionMatchingPipelineStep() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(true);
    PipelineRun pipelineRun =
        new PipelineRun(PipelineRun.PipelineType.IMPORT, "Execution Matching");
    given(pipelineTracker.current()).willReturn(pipelineRun);
    for (int i = 0; i <= 7; i++) {
      given(reportService.getReport(SEB, PENDING_TRANSACTIONS, TODAY.minusDays(i)))
          .willReturn(Optional.empty());
    }

    job.runOnSchedule();

    verify(pipelineTracker).start(PipelineRun.PipelineType.IMPORT, "Execution Matching");
    verify(pipelineTracker).stepStarted(EXECUTION_MATCHING);
    verify(pipelineTracker).stepCompleted(EXECUTION_MATCHING);
    verify(pipelineTracker, never())
        .stepFailed(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    verify(pipelineNotifier).sendCompleted(pipelineRun);
    verify(pipelineTracker).clear();
  }

  @Test
  void run_marksStepFailedWhenReconciliationThrows() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(true);
    PipelineRun pipelineRun =
        new PipelineRun(PipelineRun.PipelineType.IMPORT, "Execution Matching");
    given(pipelineTracker.current()).willReturn(pipelineRun);
    InvestmentReport today = report(TODAY);
    for (int i = 0; i <= 7; i++) {
      given(reportService.getReport(SEB, PENDING_TRANSACTIONS, TODAY.minusDays(i)))
          .willReturn(i == 0 ? Optional.of(today) : Optional.empty());
    }
    org.mockito.BDDMockito.willThrow(new RuntimeException("boom"))
        .given(reconciliationService)
        .reconcile(eq(today), any());

    job.runOnSchedule();

    verify(pipelineTracker).stepStarted(EXECUTION_MATCHING);
    verify(pipelineTracker).stepFailed(EXECUTION_MATCHING, "boom");
    verify(pipelineTracker, never()).stepCompleted(EXECUTION_MATCHING);
    verify(pipelineNotifier).sendCompleted(pipelineRun);
    verify(pipelineTracker).clear();
  }

  @Test
  void run_iteratesTodayPlusLastSevenDaysAndReconcilesEachFoundReport() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(true);
    InvestmentReport day0 = report(TODAY);
    InvestmentReport day1 = report(TODAY.minusDays(1));
    InvestmentReport day3 = report(TODAY.minusDays(3));

    for (int i = 0; i <= 7; i++) {
      LocalDate date = TODAY.minusDays(i);
      Optional<InvestmentReport> r =
          switch (i) {
            case 0 -> Optional.of(day0);
            case 1 -> Optional.of(day1);
            case 3 -> Optional.of(day3);
            default -> Optional.empty();
          };
      given(reportService.getReport(SEB, PENDING_TRANSACTIONS, date)).willReturn(r);
    }

    job.runOnSchedule();

    verify(reconciliationService).reconcile(eq(day0), any());
    verify(reconciliationService).reconcile(eq(day1), any());
    verify(reconciliationService).reconcile(eq(day3), any());
  }

  @Test
  void run_includesTodayInFallbackScan() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(true);
    InvestmentReport today = report(TODAY);
    for (int i = 0; i <= 7; i++) {
      LocalDate date = TODAY.minusDays(i);
      given(reportService.getReport(SEB, PENDING_TRANSACTIONS, date))
          .willReturn(i == 0 ? Optional.of(today) : Optional.empty());
    }

    job.runOnSchedule();

    verify(reconciliationService).reconcile(eq(today), any());
  }

  @Test
  void run_missingReportsAreSkipped() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(true);
    for (int i = 0; i <= 7; i++) {
      given(reportService.getReport(SEB, PENDING_TRANSACTIONS, TODAY.minusDays(i)))
          .willReturn(Optional.empty());
    }

    job.runOnSchedule();

    verify(reconciliationService, never()).reconcile(any(), any());
  }

  @Test
  void run_continuesAfterReconciliationException() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(true);
    InvestmentReport day1 = report(TODAY.minusDays(1));
    InvestmentReport day2 = report(TODAY.minusDays(2));
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, TODAY)).willReturn(Optional.empty());
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, TODAY.minusDays(1)))
        .willReturn(Optional.of(day1));
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, TODAY.minusDays(2)))
        .willReturn(Optional.of(day2));
    for (int i = 3; i <= 7; i++) {
      given(reportService.getReport(SEB, PENDING_TRANSACTIONS, TODAY.minusDays(i)))
          .willReturn(Optional.empty());
    }
    org.mockito.BDDMockito.willThrow(new RuntimeException("boom"))
        .given(reconciliationService)
        .reconcile(eq(day1), any());

    job.runOnSchedule();

    verify(reconciliationService).reconcile(eq(day1), any());
    verify(reconciliationService).reconcile(eq(day2), any());
  }

  @Test
  void onSebPendingTransactionReconciliationRequested_triggersRun() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(true);
    InvestmentReport today = report(TODAY);
    for (int i = 0; i <= 7; i++) {
      LocalDate date = TODAY.minusDays(i);
      given(reportService.getReport(SEB, PENDING_TRANSACTIONS, date))
          .willReturn(i == 0 ? Optional.of(today) : Optional.empty());
    }

    job.onSebPendingTransactionReconciliationRequested();

    verify(reconciliationService).reconcile(eq(today), any());
  }

  @Test
  void aManualRequestHoldsTheReconciliationLockWhileItRuns() {
    RecordingLockProvider lockProvider = RecordingLockProvider.free();
    List<Boolean> lockHeldWhileRunning = new ArrayList<>();
    given(publicHolidays.isWorkingDay(TODAY))
        .willAnswer(
            invocation -> {
              lockHeldWhileRunning.add(lockProvider.isHeld());
              return false;
            });

    jobLockedBy(lockProvider).onSebPendingTransactionReconciliationRequested();

    assertThat(lockHeldWhileRunning).containsExactly(true);
    assertThat(lockProvider.lockNames()).containsExactly("SebPendingTransactionReconciliationJob");
    assertThat(lockProvider.isHeld()).isFalse();
  }

  @Test
  void aManualRequestThatFindsTheLockHeldFailsInsteadOfReportingSuccess() {
    SebPendingTransactionReconciliationJob lockedOut =
        jobLockedBy(RecordingLockProvider.heldElsewhere());

    assertThatThrownBy(lockedOut::onSebPendingTransactionReconciliationRequested)
        .isInstanceOf(SebReconciliationAlreadyRunningException.class);

    verifyNoInteractions(
        publicHolidays, reportService, reconciliationService, pipelineTracker, pipelineNotifier);
  }

  @Test
  void theScheduledRunHoldsTheReconciliationLockWhileItRuns() {
    RecordingLockProvider lockProvider = RecordingLockProvider.free();
    List<Boolean> lockHeldWhileRunning = new ArrayList<>();
    given(publicHolidays.isWorkingDay(TODAY))
        .willAnswer(
            invocation -> {
              lockHeldWhileRunning.add(lockProvider.isHeld());
              return false;
            });

    jobLockedBy(lockProvider).runOnSchedule();

    assertThat(lockHeldWhileRunning).containsExactly(true);
    assertThat(lockProvider.lockNames()).containsExactly("SebPendingTransactionReconciliationJob");
  }

  @Test
  void theScheduledRunKeepsAnotherNodesFiringOfTheSameScheduleOutForAMinute() {
    RecordingLockProvider lockProvider = RecordingLockProvider.free();
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(false);

    jobLockedBy(lockProvider).runOnSchedule();

    assertThat(lockProvider.lockAtLeastFor()).containsExactly(Duration.ofMinutes(1));
  }

  @Test
  void theScheduledRunDoesNotRunWhileTheReplayHoldsTheLock() {
    RecordingLockProvider lockProvider = RecordingLockProvider.heldElsewhere();

    jobLockedBy(lockProvider).runOnSchedule();

    assertThat(lockProvider.lockNames()).containsExactly("SebPendingTransactionReconciliationJob");
    verifyNoInteractions(
        publicHolidays, reportService, reconciliationService, pipelineTracker, pipelineNotifier);
  }

  @Test
  void aLiveRunWhoseLockRenewalFailsStopsBeforeTheNextReportAndFailsTheRun() {
    ScheduledExecutorService keepAliveExecutor = mock(ScheduledExecutorService.class);
    AtomicReference<Runnable> keepAlive = new AtomicReference<>();
    willAnswer(
            schedule -> {
              keepAlive.set(schedule.getArgument(0));
              return mock(ScheduledFuture.class);
            })
        .given(keepAliveExecutor)
        .scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
    InvestmentReport day1 = report(TODAY.minusDays(1));
    InvestmentReport day0 = report(TODAY);
    givenLiveReports(day0, day1);
    willAnswer(
            invocation -> {
              keepAlive.get().run();
              return null;
            })
        .given(reconciliationService)
        .reconcile(eq(day0), any());
    PipelineRun pipelineRun =
        new PipelineRun(PipelineRun.PipelineType.IMPORT, "Execution Matching");
    given(pipelineTracker.current()).willReturn(pipelineRun);
    SebPendingTransactionReconciliationJob liveJob =
        jobWith(RecordingLockProvider.free().reconciliationLockAt(clock, keepAliveExecutor));

    assertThatThrownBy(liveJob::runOnSchedule)
        .isInstanceOf(SebReconciliationLockLostException.class);

    verify(reconciliationService, never()).reconcile(eq(day1), any());
    verify(pipelineTracker).stepFailed(eq(EXECUTION_MATCHING), any());
    verify(pipelineNotifier).sendCompleted(pipelineRun);
  }

  @Test
  void aLiveRunWhoseLockExpiresStopsBeforeTheNextReportAndFailsTheRun() {
    MutableClock running = new MutableClock(TODAY.atTime(7, 0).atZone(ZoneOffset.UTC).toInstant());
    InvestmentReport day1 = report(TODAY.minusDays(1));
    InvestmentReport day0 = report(TODAY);
    givenLiveReports(day0, day1);
    willAnswer(
            invocation -> {
              running.tick(3, MINUTES);
              return null;
            })
        .given(reconciliationService)
        .reconcile(eq(day0), any());
    SebPendingTransactionReconciliationJob liveJob =
        new SebPendingTransactionReconciliationJob(
            running,
            publicHolidays,
            reportService,
            reconciliationService,
            pipelineTracker,
            pipelineNotifier,
            RecordingLockProvider.free().reconciliationLockAt(running));

    assertThatThrownBy(liveJob::runOnSchedule)
        .isInstanceOf(SebReconciliationLockLostException.class);

    verify(reconciliationService, never()).reconcile(eq(day1), any());
    verify(pipelineTracker).stepFailed(eq(EXECUTION_MATCHING), any());
  }

  private void givenLiveReports(InvestmentReport day0, InvestmentReport day1) {
    lenient().when(publicHolidays.isWorkingDay(TODAY)).thenReturn(true);
    for (int i = 0; i <= 7; i++) {
      Optional<InvestmentReport> stored =
          switch (i) {
            case 0 -> Optional.of(day0);
            case 1 -> Optional.of(day1);
            default -> Optional.empty();
          };
      lenient()
          .when(reportService.getReport(SEB, PENDING_TRANSACTIONS, TODAY.minusDays(i)))
          .thenReturn(stored);
    }
  }

  private SebPendingTransactionReconciliationJob jobWith(SebReconciliationLock reconciliationLock) {
    return new SebPendingTransactionReconciliationJob(
        clock,
        publicHolidays,
        reportService,
        reconciliationService,
        pipelineTracker,
        pipelineNotifier,
        reconciliationLock);
  }

  private SebPendingTransactionReconciliationJob jobLockedBy(RecordingLockProvider provider) {
    return new SebPendingTransactionReconciliationJob(
        clock,
        publicHolidays,
        reportService,
        reconciliationService,
        pipelineTracker,
        pipelineNotifier,
        provider.reconciliationLockAt(clock));
  }

  private static InvestmentReport report(LocalDate date) {
    return InvestmentReport.builder()
        .provider(SEB)
        .reportType(PENDING_TRANSACTIONS)
        .reportDate(date)
        .build();
  }

  private static <T> T any() {
    return org.mockito.ArgumentMatchers.any();
  }
}
