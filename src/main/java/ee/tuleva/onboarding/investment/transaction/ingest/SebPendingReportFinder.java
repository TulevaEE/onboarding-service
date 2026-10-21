package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.report.SebReportHeaders;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SebPendingReportFinder {

  private static final int SEND_LAG_DAYS = 3;

  private final InvestmentReportService reportService;

  Optional<InvestmentReport> asOf(LocalDate asOfDate) {
    for (int offset = 0; offset <= SEND_LAG_DAYS; offset++) {
      Optional<InvestmentReport> candidate =
          reportService
              .getReport(SEB, PENDING_TRANSACTIONS, asOfDate.plusDays(offset))
              .filter(report -> asOfDate.equals(SebReportHeaders.asOfDate(report)));
      if (candidate.isPresent()) {
        return candidate;
      }
    }
    return Optional.empty();
  }
}
