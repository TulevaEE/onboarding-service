package ee.tuleva.onboarding.comparisons.fundvalue.validation;

import static ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.Severity.CRITICAL;
import static ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.Severity.INFO;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.comparisons.fundvalue.validation.FundValueIntegrityChecker.InstrumentCheckResult;
import ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.Discrepancy;
import ee.tuleva.onboarding.instrument.InstrumentReference;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
class FundValueIntegrityReportFormatter {

  private static final int FUND_NAME_WIDTH = 49;
  static final String CHECK_MARK = "✅";
  static final String CROSS_MARK = "❌";
  static final String WARNING_MARK = "⚠️";
  private static final String INFO_MARK = "ℹ️";
  private static final String NOT_APPLICABLE = "-";

  private FundValueIntegrityReportFormatter() {}

  static String buildSummary(
      LocalDate startDate,
      LocalDate endDate,
      List<InstrumentCheckResult> results,
      Map<InstrumentReference, String> lastPriceByInstrument) {
    StringBuilder summary = new StringBuilder();
    summary.append(
        String.format("Fund Value Integrity Check Summary (%s to %s):%n%n", startDate, endDate));

    summary.append(buildLatestDaySummary(endDate, results));
    summary.append(buildStaleSourcesSummary(results));
    summary.append("\n");
    summary.append(
        buildCrossProviderSummaryTable(startDate, endDate, results, lastPriceByInstrument));

    List<Discrepancy> criticalIssues = collectCriticalIssues(results);
    List<Discrepancy> infoIssues = collectInfoIssues(results);

    if (criticalIssues.isEmpty()) {
      if (!infoIssues.isEmpty()) {
        summary.append(
            String.format(
                "%n%s Expected Yahoo discrepancies (%d):%n", INFO_MARK, infoIssues.size()));
        summary.append(
            String.format(
                "   (Yahoo often returns intra-day prices instead of actual EOD prices)%n"));
        appendIssuesSummary(summary, infoIssues, 3);
      }
    } else {
      summary.append(
          String.format(
              "%n%s CRITICAL Issues requiring investigation (%d):%n",
              CROSS_MARK, criticalIssues.size()));
      appendIssueDetails(summary, criticalIssues);
      if (!infoIssues.isEmpty()) {
        summary.append(
            String.format(
                "%n%s Expected Yahoo discrepancies (%d - INFO only):%n",
                INFO_MARK, infoIssues.size()));
        appendIssuesSummary(summary, infoIssues, 3);
      }
    }

    return summary.toString();
  }

  static String buildLatestDaySummary(LocalDate latestDate, List<InstrumentCheckResult> results) {
    StringBuilder summary = new StringBuilder();
    summary.append(String.format("Latest Day (%s):%n", latestDate));

    List<Discrepancy> latestDayIssues =
        results.stream()
            .flatMap(r -> r.crossProviderDiscrepancies().stream())
            .filter(d -> d.date().equals(latestDate))
            .sorted(
                Comparator.comparing(Discrepancy::severity)
                    .thenComparing(d -> d.percentageDifference().negate()))
            .toList();

    if (latestDayIssues.isEmpty()) {
      summary.append(
          String.format("  %s All funds have consistent prices across providers%n", CHECK_MARK));
    } else {
      long criticalCount = latestDayIssues.stream().filter(d -> d.severity() == CRITICAL).count();
      long infoCount = latestDayIssues.stream().filter(d -> d.severity() == INFO).count();

      if (criticalCount > 0) {
        summary.append(
            String.format(
                "  %s %d critical issue(s) found - investigate before NAV calculation%n",
                CROSS_MARK, criticalCount));
        latestDayIssues.stream()
            .filter(d -> d.severity() == CRITICAL)
            .forEach(
                d ->
                    summary.append(
                        String.format(
                            "    • %s: %s=%.5f vs %s=%.5f (diff: %.4f%%)%s%n",
                            d.fundTicker(),
                            getAnchorName(d.comparisonDescription()),
                            d.anchorValue(),
                            getComparedName(d.comparisonDescription()),
                            d.comparedValue(),
                            d.percentageDifference(),
                            formatAllSources(d))));
      }
      if (infoCount > 0) {
        summary.append(
            String.format(
                "  %s %d expected Yahoo discrepancies (INFO - no action needed)%n",
                INFO_MARK, infoCount));
      }
    }

    return summary.toString();
  }

  private static String getAnchorName(String comparisonDescription) {
    return comparisonDescription.split(" vs ")[0];
  }

  private static String getComparedName(String comparisonDescription) {
    return comparisonDescription.split(" vs ")[1];
  }

  private static String buildStaleSourcesSummary(List<InstrumentCheckResult> results) {
    List<IntegrityCheckResult.StaleSource> staleSources =
        results.stream().flatMap(result -> result.staleSources().stream()).toList();
    if (staleSources.isEmpty()) {
      return "";
    }
    StringBuilder summary = new StringBuilder();
    summary.append(
        String.format(
            "%n%s Stale price sources - latest value not advancing (%d):%n",
            CROSS_MARK, staleSources.size()));
    staleSources.forEach(
        staleSource ->
            summary.append(
                String.format(
                    "  • %s [%s %s]: lastDate=%s, workingDaysBehind=%d%n",
                    staleSource.fundName(),
                    staleSource.source(),
                    staleSource.storageKey(),
                    staleSource.lastDate(),
                    staleSource.workingDaysBehind())));
    return summary.toString();
  }

  static String sourceStatus(InstrumentCheckResult result, String sourceName, LocalDate endDate) {
    if (!result.configuredSources().contains(sourceName)) {
      return NOT_APPLICABLE;
    }
    if (!result.sourcesWithData().contains(sourceName) || result.isSourceStale(sourceName)) {
      return CROSS_MARK;
    }
    if (sourceName.equals("Yahoo")) {
      return hasDiscrepancyOn(result, endDate, sourceName) ? WARNING_MARK : CHECK_MARK;
    }
    return hasCriticalDiscrepancyOn(result, endDate, sourceName) ? CROSS_MARK : CHECK_MARK;
  }

  private static boolean hasDiscrepancyOn(
      InstrumentCheckResult result, LocalDate date, String sourceName) {
    return result.crossProviderDiscrepancies().stream()
        .anyMatch(d -> d.date().equals(date) && involves(d, sourceName));
  }

  private static boolean hasCriticalDiscrepancyOn(
      InstrumentCheckResult result, LocalDate date, String sourceName) {
    return result.crossProviderDiscrepancies().stream()
        .anyMatch(
            d -> d.date().equals(date) && d.severity() == CRITICAL && involves(d, sourceName));
  }

  private static boolean involves(Discrepancy discrepancy, String sourceName) {
    return Arrays.asList(discrepancy.comparisonDescription().split(" vs ")).contains(sourceName);
  }

  private static String buildCrossProviderSummaryTable(
      LocalDate startDate,
      LocalDate endDate,
      List<InstrumentCheckResult> results,
      Map<InstrumentReference, String> lastPriceByInstrument) {
    StringBuilder table = new StringBuilder();
    table.append(String.format("Cross-Provider Comparison (%s):%n", endDate));
    table.append(
        String.format(
            "  Each source is compared against the highest-priority source with data"
                + " — mismatch severity: vs Yahoo → INFO, all others → CRITICAL%n%n"));
    table.append(formatCrossProviderHeader());
    table.append(formatCrossProviderSeparator());

    for (InstrumentCheckResult result : results) {
      table.append(
          formatCrossProviderRow(
              truncateFundName(result.instrument().getDisplayName()),
              sourceStatus(result, "EODHD", endDate),
              sourceStatus(result, "Exchange", endDate),
              sourceStatus(result, "BlackRock", endDate),
              sourceStatus(result, "Morningstar", endDate),
              sourceStatus(result, "Yahoo", endDate),
              requireNonNull(
                  lastPriceByInstrument.get(result.instrument()),
                  "Missing last price: instrument=" + result.instrument())));
    }
    table.append(formatCrossProviderFooter());

    return table.toString();
  }

  private static List<Discrepancy> collectCriticalIssues(List<InstrumentCheckResult> results) {
    return results.stream()
        .flatMap(r -> r.crossProviderDiscrepancies().stream())
        .filter(d -> d.severity() == CRITICAL)
        .toList();
  }

  private static List<Discrepancy> collectInfoIssues(List<InstrumentCheckResult> results) {
    return results.stream()
        .flatMap(r -> r.crossProviderDiscrepancies().stream())
        .filter(d -> d.severity() == INFO)
        .toList();
  }

  static void appendIssueDetails(StringBuilder summary, List<Discrepancy> issues) {
    issues.stream()
        .sorted(Comparator.comparing(Discrepancy::date).reversed())
        .limit(10)
        .forEach(
            d ->
                summary.append(
                    String.format(
                        "  • %s [%s]: %s %.5f vs %.5f, diff=%.5f (%.4f%%)%s%n",
                        d.fundTicker(),
                        d.date(),
                        d.comparisonDescription(),
                        d.anchorValue(),
                        d.comparedValue(),
                        d.difference(),
                        d.percentageDifference(),
                        formatAllSources(d))));
    if (issues.size() > 10) {
      summary.append(String.format("  ... and %d more%n", issues.size() - 10));
    }
  }

  static String formatAllSources(Discrepancy discrepancy) {
    if (discrepancy.allSourceValues().size() <= 2) {
      return "";
    }
    return discrepancy.allSourceValues().stream()
        .map(sourceValue -> sourceValue.source() + "=" + sourceValue.value().toPlainString())
        .collect(joining(", ", " | all sources: ", ""));
  }

  private static void appendIssuesSummary(
      StringBuilder summary, List<Discrepancy> issues, int limit) {
    Map<String, Long> countByFund =
        issues.stream().collect(groupingBy(Discrepancy::fundTicker, counting()));

    countByFund.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(limit)
        .forEach(
            entry ->
                summary.append(
                    String.format("  • %s: %d occurrences%n", entry.getKey(), entry.getValue())));

    if (countByFund.size() > limit) {
      summary.append(String.format("  ... and %d more funds%n", countByFund.size() - limit));
    }
  }

  static String truncateFundName(String name) {
    if (name.length() <= FUND_NAME_WIDTH) {
      return name;
    }
    return name.substring(0, FUND_NAME_WIDTH - 3) + "...";
  }

  private static String formatCrossProviderHeader() {
    return String.format(
        "┌─%-"
            + FUND_NAME_WIDTH
            + "s─┬────────┬────────────┬────────────┬──────────────┬────────┬───────────────────────────┐%n"
            + "│ %-"
            + FUND_NAME_WIDTH
            + "s │ %-6s │ %-10s │ %-10s │ %-12s │ %-6s │ %-25s │%n",
        "─".repeat(FUND_NAME_WIDTH),
        "Fund",
        "EODHD",
        "Exchange",
        "BlackRock",
        "Morningstar",
        "Yahoo",
        "Last Price");
  }

  static String formatCrossProviderSeparator() {
    return String.format(
        "├─%-"
            + FUND_NAME_WIDTH
            + "s─┼────────┼────────────┼────────────┼──────────────┼────────┼───────────────────────────┤%n",
        "─".repeat(FUND_NAME_WIDTH));
  }

  private static String formatCrossProviderRow(
      String fund,
      String eodhd,
      String exchange,
      String blackrock,
      String morningstar,
      String yahoo,
      String lastPrice) {
    return String.format(
        "│ %-" + FUND_NAME_WIDTH + "s │ %s │ %s │ %s │ %s │ %s │ %s │%n",
        fund,
        padCrossProviderStatus(eodhd, 6),
        padCrossProviderStatus(exchange, 10),
        padCrossProviderStatus(blackrock, 10),
        padCrossProviderStatus(morningstar, 12),
        padCrossProviderStatus(yahoo, 6),
        padCrossProviderStatus(lastPrice, 25));
  }

  static String padCrossProviderStatus(String status, int width) {
    return String.format("%-" + width + "s", status);
  }

  static String formatCrossProviderFooter() {
    return String.format(
        "└─%-"
            + FUND_NAME_WIDTH
            + "s─┴────────┴────────────┴────────────┴──────────────┴────────┴───────────────────────────┘%n",
        "─".repeat(FUND_NAME_WIDTH));
  }

  static String buildCriticalAlert(List<InstrumentCheckResult> results) {
    StringBuilder sb =
        new StringBuilder(
            "SUSPICIOUS PRICE DATA — verify instruments/sources before NAV calculation at 11:00\n");
    results.stream()
        .flatMap(result -> result.staleSources().stream())
        .forEach(
            stale ->
                sb.append(
                    String.format(
                        "  STALE: %s %s last=%s (%d working days behind)%n",
                        stale.fundName(),
                        stale.source(),
                        stale.lastDate(),
                        stale.workingDaysBehind())));
    collectCriticalIssues(results)
        .forEach(
            d ->
                sb.append(
                    String.format(
                        "  PRICE DISCREPANCY: %s [%s] %s anchor=%s vs compared=%s (%s%%)%s%n",
                        d.fundTicker(),
                        d.date(),
                        d.comparisonDescription(),
                        d.anchorValue().toPlainString(),
                        d.comparedValue().toPlainString(),
                        d.percentageDifference().toPlainString(),
                        formatAllSources(d))));
    return sb.toString().stripTrailing();
  }
}
