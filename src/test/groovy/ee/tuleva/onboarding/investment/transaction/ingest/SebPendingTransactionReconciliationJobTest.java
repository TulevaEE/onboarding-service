package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

  @Spy private Clock clock = Clock.fixed(TODAY.atStartOfDay(TALLINN).toInstant(), TALLINN);

  @Mock private InvestmentReportService reportService;
  @Mock private SebPendingTransactionReconciliationService reconciliationService;

  @InjectMocks private SebPendingTransactionReconciliationJob job;

  @Test
  void run_iteratesLastSevenDaysAndReconcilesEachFoundReport() {
    InvestmentReport day1 = report(TODAY.minusDays(1));
    InvestmentReport day3 = report(TODAY.minusDays(3));

    for (int i = 1; i <= 7; i++) {
      LocalDate date = TODAY.minusDays(i);
      Optional<InvestmentReport> r =
          switch (i) {
            case 1 -> Optional.of(day1);
            case 3 -> Optional.of(day3);
            default -> Optional.empty();
          };
      given(reportService.getReport(SEB, PENDING_TRANSACTIONS, date)).willReturn(r);
    }

    job.run();

    verify(reconciliationService).reconcile(day1);
    verify(reconciliationService).reconcile(day3);
  }

  @Test
  void run_missingReportsAreSkipped() {
    for (int i = 1; i <= 7; i++) {
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
