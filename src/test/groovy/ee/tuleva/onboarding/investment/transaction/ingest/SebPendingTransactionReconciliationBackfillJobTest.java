package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.investment.event.RunSebPendingTransactionReconciliationBackfillRequested;
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
class SebPendingTransactionReconciliationBackfillJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 5, 18);
  private static final int BACKFILL_DAYS = 365;

  @Spy private Clock clock = Clock.fixed(TODAY.atStartOfDay(TALLINN).toInstant(), TALLINN);

  @Mock private InvestmentReportService reportService;
  @Mock private SebPendingTransactionReconciliationService reconciliationService;

  @InjectMocks private SebPendingTransactionReconciliationBackfillJob job;

  @Test
  void run_iteratesBackfillWindowAndReconcilesEachFoundReport() {
    InvestmentReport day0 = report(TODAY);
    InvestmentReport day10 = report(TODAY.minusDays(10));
    InvestmentReport day27 = report(TODAY.minusDays(27));

    for (int i = 0; i <= BACKFILL_DAYS; i++) {
      LocalDate date = TODAY.minusDays(i);
      Optional<InvestmentReport> r =
          switch (i) {
            case 0 -> Optional.of(day0);
            case 10 -> Optional.of(day10);
            case 27 -> Optional.of(day27);
            default -> Optional.empty();
          };
      given(reportService.getReport(SEB, PENDING_TRANSACTIONS, date)).willReturn(r);
    }

    job.run();

    verify(reconciliationService).reconcile(day0);
    verify(reconciliationService).reconcile(day10);
    verify(reconciliationService).reconcile(day27);
    verify(reconciliationService, times(3)).reconcile(any());
  }

  @Test
  void run_skipsDatesWithoutReports() {
    for (int i = 0; i <= BACKFILL_DAYS; i++) {
      given(reportService.getReport(SEB, PENDING_TRANSACTIONS, TODAY.minusDays(i)))
          .willReturn(Optional.empty());
    }

    job.run();

    verify(reconciliationService, never()).reconcile(any());
  }

  @Test
  void run_continuesAfterReconciliationException() {
    InvestmentReport day1 = report(TODAY.minusDays(1));
    InvestmentReport day2 = report(TODAY.minusDays(2));
    for (int i = 0; i <= BACKFILL_DAYS; i++) {
      LocalDate date = TODAY.minusDays(i);
      Optional<InvestmentReport> r =
          switch (i) {
            case 1 -> Optional.of(day1);
            case 2 -> Optional.of(day2);
            default -> Optional.empty();
          };
      given(reportService.getReport(SEB, PENDING_TRANSACTIONS, date)).willReturn(r);
    }
    org.mockito.BDDMockito.willThrow(new RuntimeException("boom"))
        .given(reconciliationService)
        .reconcile(day1);

    job.run();

    verify(reconciliationService).reconcile(day1);
    verify(reconciliationService).reconcile(day2);
  }

  @Test
  void onSebPendingTransactionReconciliationBackfillRequested_triggersRun() {
    for (int i = 0; i <= BACKFILL_DAYS; i++) {
      given(reportService.getReport(SEB, PENDING_TRANSACTIONS, TODAY.minusDays(i)))
          .willReturn(Optional.empty());
    }

    job.onSebPendingTransactionReconciliationBackfillRequested(
        new RunSebPendingTransactionReconciliationBackfillRequested());

    verify(reportService, times(BACKFILL_DAYS + 1)).getReport(any(), any(), any());
  }

  private static InvestmentReport report(LocalDate date) {
    return InvestmentReport.builder()
        .provider(SEB)
        .reportType(PENDING_TRANSACTIONS)
        .reportDate(date)
        .build();
  }
}
