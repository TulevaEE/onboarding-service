package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.event.PipelineStep.EXECUTION_MATCHING;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.event.PipelineNotifier;
import ee.tuleva.onboarding.investment.event.PipelineRun;
import ee.tuleva.onboarding.investment.event.PipelineTracker;
import ee.tuleva.onboarding.investment.event.RunSebPendingTransactionReconciliationRequested;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

  @InjectMocks private SebPendingTransactionReconciliationJob job;

  @Test
  void run_skipsOnNonWorkingDay() {
    Clock saturdayClock = Clock.fixed(SATURDAY.atStartOfDay(TALLINN).toInstant(), TALLINN);
    given(clock.instant()).willReturn(saturdayClock.instant());
    given(clock.getZone()).willReturn(saturdayClock.getZone());
    given(publicHolidays.isWorkingDay(SATURDAY)).willReturn(false);

    job.run();

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

    job.run();

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
        .reconcile(today);

    job.run();

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

    job.run();

    verify(reconciliationService).reconcile(day0);
    verify(reconciliationService).reconcile(day1);
    verify(reconciliationService).reconcile(day3);
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

    job.run();

    verify(reconciliationService).reconcile(today);
  }

  @Test
  void run_missingReportsAreSkipped() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(true);
    for (int i = 0; i <= 7; i++) {
      given(reportService.getReport(SEB, PENDING_TRANSACTIONS, TODAY.minusDays(i)))
          .willReturn(Optional.empty());
    }

    job.run();

    verify(reconciliationService, never()).reconcile(any());
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
        .reconcile(day1);

    job.run();

    verify(reconciliationService).reconcile(day1);
    verify(reconciliationService).reconcile(day2);
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

    job.onSebPendingTransactionReconciliationRequested(
        new RunSebPendingTransactionReconciliationRequested());

    verify(reconciliationService).reconcile(today);
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
