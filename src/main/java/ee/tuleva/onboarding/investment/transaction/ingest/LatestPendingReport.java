package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.report.SebReportAsOfDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class LatestPendingReport {

  private final InvestmentReportService reportService;
  private final SebPendingTransactionExtractor extractor;
  private final SebReportAsOfDate sebReportAsOfDate;

  Optional<PendingListing> listing() {
    return reportService.getLatestReport(SEB, PENDING_TRANSACTIONS).flatMap(this::listingOf);
  }

  private Optional<PendingListing> listingOf(InvestmentReport report) {
    SebPendingTransactionExtractor.ExtractionResult extraction =
        extractor.extractWithDiagnostics(report);
    if (!extraction.isComplete()) {
      log.warn(
          "Latest SEB pending report has malformed rows, no settlement from the bank:"
              + " reportDate={}, malformedCount={}",
          report.getReportDate(),
          extraction.malformedCount());
      return Optional.empty();
    }
    List<SebPendingTransactionRow> rows = extraction.rows();
    return Optional.of(
        new PendingListing(
            sebReportAsOfDate.resolveOrFallBackToReportDate(
                PENDING_TRANSACTIONS,
                report.getReportDate(),
                report.getMetadata(),
                report.getRawData()),
            rows.stream()
                .map(SebPendingTransactionRow::clientRef)
                .filter(Objects::nonNull)
                .collect(toSet()),
            rows.stream()
                .map(SebPendingTransactionRow::ourRef)
                .filter(Objects::nonNull)
                .collect(toSet())));
  }
}
