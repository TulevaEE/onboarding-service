package ee.tuleva.onboarding.kyb.screener;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;
import static java.util.Comparator.nullsLast;

import ee.tuleva.onboarding.ariregister.AnnualReport;
import ee.tuleva.onboarding.ariregister.FinancialYear;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class AnnualReportsMetadata {

  private static final String FINANCIAL_YEARS = "financialYears";
  private static final String ANNUAL_REPORTS = "annualReports";

  private static final Comparator<FinancialYear> FINANCIAL_YEAR_ORDER =
      comparing(FinancialYear::validFrom, nullsFirst(naturalOrder()))
          .thenComparing(FinancialYear::validTo, nullsLast(naturalOrder()))
          .thenComparing(FinancialYear::start, nullsLast(naturalOrder()))
          .thenComparing(FinancialYear::end, nullsLast(naturalOrder()));

  private static final Comparator<AnnualReport> REPORT_ORDER =
      comparing(AnnualReport::periodEnd, nullsLast(naturalOrder()))
          .thenComparing(AnnualReport::periodStart, nullsLast(naturalOrder()));

  private AnnualReportsMetadata() {}

  static Map<String, Object> of(
      final List<FinancialYear> financialYears, final List<AnnualReport> annualReports) {
    return Map.of(
        FINANCIAL_YEARS,
        financialYears.stream()
            .sorted(FINANCIAL_YEAR_ORDER)
            .map(AnnualReportsMetadata::financialYearMetadata)
            .toList(),
        ANNUAL_REPORTS,
        annualReports.stream()
            .sorted(REPORT_ORDER)
            .map(AnnualReportsMetadata::reportMetadata)
            .toList());
  }

  private static Map<String, Object> financialYearMetadata(FinancialYear year) {
    var metadata = new HashMap<String, Object>();
    putPresent(metadata, "start", year.start());
    putPresent(metadata, "end", year.end());
    putPresent(metadata, "validFrom", year.validFrom());
    putPresent(metadata, "validTo", year.validTo());
    return Map.copyOf(metadata);
  }

  private static Map<String, Object> reportMetadata(AnnualReport report) {
    var metadata = new HashMap<String, Object>();
    putPresent(metadata, "periodStart", report.periodStart());
    putPresent(metadata, "periodEnd", report.periodEnd());
    return Map.copyOf(metadata);
  }

  private static void putPresent(Map<String, Object> metadata, String key, @Nullable Object value) {
    if (value != null) {
      metadata.put(key, value.toString());
    }
  }
}
