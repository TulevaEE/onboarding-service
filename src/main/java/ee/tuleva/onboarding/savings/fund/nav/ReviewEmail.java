package ee.tuleva.onboarding.savings.fund.nav;

import java.util.List;
import java.util.Optional;

enum ReviewEmail {
  SEND("The CSV report was emailed to funds@tuleva.ee."),
  ALREADY_SENT(
      "The CSV report was emailed to funds@tuleva.ee earlier, and its NAV and AUM have not changed since.");

  private static final String UNITS_ACCOUNT_TYPE = "UNITS";

  private final String csvLine;

  ReviewEmail(String csvLine) {
    this.csvLine = csvLine;
  }

  String csvLine() {
    return csvLine;
  }

  static ReviewEmail comparing(
      Optional<NavReportRow> earlierUnsent, List<NavReportRow> reportRows) {
    return earlierUnsent
        .flatMap(earlier -> unitsRowOf(reportRows).filter(now -> hasTheSameNavAndAum(earlier, now)))
        .map(unchanged -> ALREADY_SENT)
        .orElse(SEND);
  }

  private static Optional<NavReportRow> unitsRowOf(List<NavReportRow> reportRows) {
    return reportRows.stream()
        .filter(row -> UNITS_ACCOUNT_TYPE.equals(row.getAccountType()))
        .findFirst();
  }

  private static boolean hasTheSameNavAndAum(NavReportRow earlier, NavReportRow now) {
    return earlier.getMarketPrice().compareTo(now.getMarketPrice()) == 0
        && earlier.getMarketValue().compareTo(now.getMarketValue()) == 0;
  }
}
