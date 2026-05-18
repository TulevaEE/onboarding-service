package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SWEDBANK;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebPendingTransactionReconciliationListenerTest {

  @Mock private InvestmentReportService reportService;
  @Mock private SebPendingTransactionReconciliationService reconciliationService;

  @InjectMocks private SebPendingTransactionReconciliationListener listener;

  @Test
  void onReportImportCompleted_sebPendingTransactions_loadsReportAndReconciles() {
    LocalDate reportDate = LocalDate.of(2026, 5, 13);
    InvestmentReport report =
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(reportDate)
            .build();
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, reportDate))
        .willReturn(Optional.of(report));

    listener.onReportImportCompleted(
        new ReportImportCompleted(SEB, PENDING_TRANSACTIONS, reportDate, 1));

    verify(reconciliationService).reconcile(report);
  }

  @Test
  void onReportImportCompleted_swedbankPendingTransactions_isIgnored() {
    listener.onReportImportCompleted(
        new ReportImportCompleted(SWEDBANK, PENDING_TRANSACTIONS, LocalDate.of(2026, 5, 13), 1));

    verifyNoInteractions(reportService, reconciliationService);
  }

  @Test
  void onReportImportCompleted_sebPositions_isIgnored() {
    listener.onReportImportCompleted(
        new ReportImportCompleted(SEB, POSITIONS, LocalDate.of(2026, 5, 13), 1));

    verifyNoInteractions(reportService, reconciliationService);
  }

  @Test
  void onReportImportCompleted_reportMissing_doesNothing() {
    LocalDate reportDate = LocalDate.of(2026, 5, 13);
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, reportDate))
        .willReturn(Optional.empty());

    listener.onReportImportCompleted(
        new ReportImportCompleted(SEB, PENDING_TRANSACTIONS, reportDate, 1));

    verify(reconciliationService, never()).reconcile(any());
  }

  private static <T> T any() {
    return org.mockito.ArgumentMatchers.any();
  }
}
