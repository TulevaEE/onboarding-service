package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebPendingTransactionReconciliationService.TRUNCATION_ROW_DROP_RATIO;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.report.SebReportHeaders;
import java.time.LocalDate;
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
    LocalDate asOfDate = SebReportHeaders.asOfDate(report);
    if (asOfDate == null) {
      log.warn(
          "Latest SEB pending report has no readable 'As of' date, no settlement from the bank:"
              + " reportDate={}",
          report.getReportDate());
      return Optional.empty();
    }
    List<SebPendingTransactionRow> rows = extraction.rows();
    if (rows.isEmpty() || lostRowsSincePriorReport(report, rows.size())) {
      log.warn(
          "Latest SEB pending report is empty or may be truncated, no settlement from the bank:"
              + " reportDate={}, rowCount={}",
          report.getReportDate(),
          rows.size());
      return Optional.empty();
    }
    return Optional.of(
        new PendingListing(
            asOfDate,
            rows.stream()
                .map(SebPendingTransactionRow::clientRef)
                .filter(Objects::nonNull)
                .collect(toSet()),
            rows.stream()
                .map(SebPendingTransactionRow::ourRef)
                .filter(Objects::nonNull)
                .collect(toSet()),
            rows.stream()
                .filter(row -> row.clientRef() == null && row.ourRef() == null)
                .map(SebPendingTransactionRow::isin)
                .filter(Objects::nonNull)
                .collect(toSet())));
  }

  private boolean lostRowsSincePriorReport(InvestmentReport report, int rowCount) {
    return reportService
        .getPriorReport(SEB, PENDING_TRANSACTIONS, report.getReportDate())
        .map(prior -> extractor.extractWithDiagnostics(prior).rows().size())
        .filter(priorRowCount -> rowCount < priorRowCount * (1 - TRUNCATION_ROW_DROP_RATIO))
        .isPresent();
  }
}
