package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.SebReportHeaders;
import java.time.LocalDate;
import java.util.Optional;

enum SettlementCheck {
  CHECKED,
  SKIPPED_EMPTY_REPORT,
  SKIPPED_NO_MATCHED_ROW,
  SKIPPED_MALFORMED_ROWS,
  SKIPPED_NOT_LATEST_REPORT,
  SKIPPED_POSSIBLE_TRUNCATION;

  Optional<LocalDate> trustedAsOfDate(InvestmentReport report) {
    return trustsTheReport()
        ? Optional.ofNullable(SebReportHeaders.asOfDate(report))
        : Optional.empty();
  }

  private boolean trustsTheReport() {
    return this == CHECKED || this == SKIPPED_NOT_LATEST_REPORT;
  }
}
