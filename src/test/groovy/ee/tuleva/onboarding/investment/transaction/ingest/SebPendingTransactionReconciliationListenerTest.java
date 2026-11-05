package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SWEDBANK;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.investment.event.trigger.JobTriggerQueue;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebPendingTransactionReconciliationListenerTest {

  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 5, 13);

  @Mock private InvestmentReportService reportService;
  @Mock private SebPendingTransactionReconciliationService reconciliationService;
  @Mock private JobTriggerQueue jobTriggerQueue;

  private final Clock clock = Clock.fixed(Instant.parse("2026-05-13T15:20:00Z"), ZoneOffset.UTC);

  @Test
  void anImportedSebPendingReportIsReconciledUnderTheReconciliationLock() {
    RecordingLockProvider lockProvider = RecordingLockProvider.free();
    InvestmentReport report = sebPendingReport();
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, REPORT_DATE))
        .willReturn(Optional.of(report));
    List<Boolean> lockHeldWhileReconciling = new ArrayList<>();
    willAnswer(
            invocation -> {
              lockHeldWhileReconciling.add(lockProvider.isHeld());
              return null;
            })
        .given(reconciliationService)
        .reconcile(eq(report), any());

    listenerLockedBy(lockProvider)
        .onReportImportCompleted(
            new ReportImportCompleted(SEB, PENDING_TRANSACTIONS, REPORT_DATE, 1));

    assertThat(lockHeldWhileReconciling).containsExactly(true);
    verifyNoInteractions(jobTriggerQueue);
  }

  @Test
  void anImportFinishingWhileAReplayHoldsTheLockDefersItsReconciliationToALiveRun() {
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, REPORT_DATE))
        .willReturn(Optional.of(sebPendingReport()));

    listenerLockedBy(RecordingLockProvider.heldElsewhere())
        .onReportImportCompleted(
            new ReportImportCompleted(SEB, PENDING_TRANSACTIONS, REPORT_DATE, 1));

    verify(reconciliationService, never()).reconcile(any(), any());
    verify(jobTriggerQueue).enqueueUnlessPending("SebPendingTransactionReconciliationJob");
  }

  @Test
  void onReportImportCompleted_swedbankPendingTransactions_isIgnored() {
    listenerLockedBy(RecordingLockProvider.free())
        .onReportImportCompleted(
            new ReportImportCompleted(SWEDBANK, PENDING_TRANSACTIONS, REPORT_DATE, 1));

    verifyNoInteractions(reportService, reconciliationService, jobTriggerQueue);
  }

  @Test
  void onReportImportCompleted_sebPositions_isIgnored() {
    listenerLockedBy(RecordingLockProvider.free())
        .onReportImportCompleted(new ReportImportCompleted(SEB, POSITIONS, REPORT_DATE, 1));

    verifyNoInteractions(reportService, reconciliationService, jobTriggerQueue);
  }

  @Test
  void onReportImportCompleted_reportMissing_doesNothing() {
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, REPORT_DATE))
        .willReturn(Optional.empty());

    listenerLockedBy(RecordingLockProvider.free())
        .onReportImportCompleted(
            new ReportImportCompleted(SEB, PENDING_TRANSACTIONS, REPORT_DATE, 1));

    verify(reconciliationService, never()).reconcile(any(), any());
    verifyNoInteractions(jobTriggerQueue);
  }

  private SebPendingTransactionReconciliationListener listenerLockedBy(
      RecordingLockProvider lockProvider) {
    return new SebPendingTransactionReconciliationListener(
        reportService,
        reconciliationService,
        lockProvider.reconciliationLockAt(clock),
        jobTriggerQueue);
  }

  private static InvestmentReport sebPendingReport() {
    return InvestmentReport.builder()
        .provider(SEB)
        .reportType(PENDING_TRANSACTIONS)
        .reportDate(REPORT_DATE)
        .build();
  }
}
