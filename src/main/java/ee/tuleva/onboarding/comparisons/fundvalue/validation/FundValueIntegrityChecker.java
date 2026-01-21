package ee.tuleva.onboarding.comparisons.fundvalue.validation;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.YahooFundValueRetriever;
import ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.Discrepancy;
import ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.MissingData;
import ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.OrphanedData;
import ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.Severity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundValueIntegrityChecker {

  private static final int DATABASE_SCALE = 5;
  private static final BigDecimal SAME_PROVIDER_THRESHOLD_PERCENT = new BigDecimal("0.0001");
  private static final BigDecimal CROSS_PROVIDER_THRESHOLD_PERCENT = new BigDecimal("0.001");
  private static final int FUND_NAME_WIDTH = 49;
  private static final String CHECK_MARK = "✅";
  private static final String CROSS_MARK = "❌";
  private static final String WARNING_MARK = "⚠️";
  private static final String INFO_MARK = "ℹ️";
  private static final String NOT_APPLICABLE = "-";

  private final YahooFundValueRetriever yahooFundValueRetriever;
  private final FundValueRepository fundValueRepository;

  record TickerCheckResult(
      FundTicker ticker,
      IntegrityCheckResult yahooVsDbResult,
      boolean eodhdOk,
      boolean exchangeOk,
      boolean yahooOk,
      List<Discrepancy> crossProviderDiscrepancies) {

    boolean hasYahooVsDbIssues() {
      return yahooVsDbResult != null && yahooVsDbResult.hasIssues();
    }

    boolean hasCrossProviderIssues() {
      return !crossProviderDiscrepancies.isEmpty();
    }

    boolean hasCriticalIssues() {
      return crossProviderDiscrepancies.stream().anyMatch(d -> d.severity() == Severity.CRITICAL);
    }
  }

  @Scheduled(cron = "0 30 * * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "FundValueIntegrityChecker_performIntegrityCheck",
      lockAtMostFor = "55m",
      lockAtLeastFor = "5m")
  public void performIntegrityCheck() {
    LocalDate endDate = LocalDate.now().minusDays(1);
    LocalDate crossProviderStartDate = endDate.minusDays(30);

    List<TickerCheckResult> results = collectAllResults(crossProviderStartDate, endDate);
    logSummary(crossProviderStartDate, endDate, results);
  }

  private List<TickerCheckResult> collectAllResults(LocalDate startDate, LocalDate endDate) {
    return Arrays.stream(FundTicker.values())
        .map(
            ticker -> {
              IntegrityCheckResult yahooVsDbResult =
                  verifyFundDataIntegrity(ticker.getYahooTicker(), startDate, endDate);
              CrossProviderCheckResult crossProviderResult =
                  checkCrossProviderIntegrityInternal(ticker, startDate, endDate);

              return new TickerCheckResult(
                  ticker,
                  yahooVsDbResult,
                  crossProviderResult.eodhdOk(),
                  crossProviderResult.exchangeOk(),
                  crossProviderResult.yahooOk(),
                  crossProviderResult.discrepancies());
            })
        .toList();
  }

  private record CrossProviderCheckResult(
      boolean eodhdOk, boolean exchangeOk, boolean yahooOk, List<Discrepancy> discrepancies) {}

  void checkYahooVsDatabaseIntegrity(String fundTicker, LocalDate startDate, LocalDate endDate) {
    verifyFundDataIntegrity(fundTicker, startDate, endDate);
  }

  List<Discrepancy> checkCrossProviderIntegrity(
      FundTicker ticker, LocalDate startDate, LocalDate endDate) {
    return findCrossProviderDiscrepancies(ticker, startDate, endDate);
  }

  private CrossProviderCheckResult checkCrossProviderIntegrityInternal(
      FundTicker ticker, LocalDate startDate, LocalDate endDate) {
    Map<LocalDate, BigDecimal> eodhdByDate =
        convertToDateValueMap(
            fundValueRepository.findValuesBetweenDates(
                ticker.getEodhdTicker(), startDate, endDate));
    Map<LocalDate, BigDecimal> yahooByDate =
        convertToDateValueMap(
            fundValueRepository.findValuesBetweenDates(
                ticker.getYahooTicker(), startDate, endDate));

    List<Discrepancy> discrepancies = new ArrayList<>();

    boolean exchangeOk = true;
    Optional<String> xetraKey = ticker.getXetraStorageKey();
    Optional<String> euronextKey = ticker.getEuronextParisStorageKey();

    if (xetraKey.isPresent()) {
      Map<LocalDate, BigDecimal> xetraByDate =
          convertToDateValueMap(
              fundValueRepository.findValuesBetweenDates(xetraKey.get(), startDate, endDate));
      List<Discrepancy> exchangeVsEodhdDiscrepancies =
          compareProviders(
              ticker.getDisplayName(),
              "Deutsche Börse",
              xetraByDate,
              "EODHD",
              eodhdByDate,
              Severity.CRITICAL,
              "Exchange vs EODHD");
      discrepancies.addAll(exchangeVsEodhdDiscrepancies);
      exchangeOk = exchangeVsEodhdDiscrepancies.isEmpty() && !xetraByDate.isEmpty();
    } else if (euronextKey.isPresent()) {
      Map<LocalDate, BigDecimal> euronextByDate =
          convertToDateValueMap(
              fundValueRepository.findValuesBetweenDates(euronextKey.get(), startDate, endDate));
      List<Discrepancy> exchangeVsEodhdDiscrepancies =
          compareProviders(
              ticker.getDisplayName(),
              "Euronext",
              euronextByDate,
              "EODHD",
              eodhdByDate,
              Severity.CRITICAL,
              "Exchange vs EODHD");
      discrepancies.addAll(exchangeVsEodhdDiscrepancies);
      exchangeOk = exchangeVsEodhdDiscrepancies.isEmpty() && !euronextByDate.isEmpty();
    }

    List<Discrepancy> eodhdVsYahooDiscrepancies =
        compareProviders(
            ticker.getDisplayName(),
            "EODHD",
            eodhdByDate,
            "Yahoo",
            yahooByDate,
            Severity.INFO,
            "EODHD vs Yahoo");
    discrepancies.addAll(eodhdVsYahooDiscrepancies);

    boolean eodhdOk = !eodhdByDate.isEmpty();
    boolean yahooOk = eodhdVsYahooDiscrepancies.isEmpty() && !yahooByDate.isEmpty();

    return new CrossProviderCheckResult(eodhdOk, exchangeOk, yahooOk, discrepancies);
  }

  IntegrityCheckResult verifyFundDataIntegrity(
      String fundTicker, LocalDate startDate, LocalDate endDate) {
    try {
      List<FundValue> yahooFinanceValues = fetchYahooFinanceData(fundTicker, startDate, endDate);
      List<FundValue> databaseFundValues = fetchDatabaseData(fundTicker, startDate, endDate);

      Map<LocalDate, BigDecimal> yahooValuesByDate = convertToDateValueMap(yahooFinanceValues);
      Map<LocalDate, BigDecimal> databaseValuesByDate = convertToDateValueMap(databaseFundValues);

      List<Discrepancy> discrepancies =
          findDiscrepancies(fundTicker, yahooValuesByDate, databaseValuesByDate);
      List<MissingData> missingData =
          findMissingData(fundTicker, yahooValuesByDate, databaseValuesByDate);
      List<OrphanedData> orphanedData =
          findOrphanedData(fundTicker, yahooValuesByDate, databaseValuesByDate);

      return IntegrityCheckResult.builder()
          .discrepancies(discrepancies)
          .missingData(missingData)
          .orphanedData(orphanedData)
          .build();
    } catch (Exception e) {
      log.error("Failed to verify data integrity for fund {}: {}", fundTicker, e.getMessage(), e);
      return IntegrityCheckResult.empty();
    }
  }

  private List<FundValue> fetchYahooFinanceData(
      String fundTicker, LocalDate startDate, LocalDate endDate) {
    return yahooFundValueRetriever.retrieveValuesForRange(startDate, endDate).stream()
        .filter(fundValue -> fundValue.key().equals(fundTicker))
        .toList();
  }

  private List<FundValue> fetchDatabaseData(
      String fundTicker, LocalDate startDate, LocalDate endDate) {
    return fundValueRepository.findValuesBetweenDates(fundTicker, startDate, endDate);
  }

  private Map<LocalDate, BigDecimal> convertToDateValueMap(List<FundValue> fundValues) {
    return fundValues.stream()
        .collect(
            toMap(
                FundValue::date,
                FundValue::value,
                (existingValue, duplicateValue) -> existingValue));
  }

  private List<Discrepancy> findDiscrepancies(
      String fundTicker,
      Map<LocalDate, BigDecimal> yahooValuesByDate,
      Map<LocalDate, BigDecimal> databaseValuesByDate) {

    return databaseValuesByDate.entrySet().stream()
        .filter(entry -> yahooValuesByDate.containsKey(entry.getKey()))
        .map(
            entry -> {
              LocalDate date = entry.getKey();
              BigDecimal databaseValue = entry.getValue();
              BigDecimal yahooValue = yahooValuesByDate.get(date);

              BigDecimal normalizedDbValue = databaseValue.setScale(DATABASE_SCALE, HALF_UP);
              BigDecimal normalizedYahooValue = yahooValue.setScale(DATABASE_SCALE, HALF_UP);

              BigDecimal percentageDifference =
                  calculatePercentageDifference(normalizedDbValue, normalizedYahooValue);
              if (percentageDifference.compareTo(SAME_PROVIDER_THRESHOLD_PERCENT) > 0) {
                BigDecimal difference = normalizedDbValue.subtract(normalizedYahooValue).abs();
                return new Discrepancy(
                    fundTicker,
                    date,
                    normalizedDbValue,
                    normalizedYahooValue,
                    difference,
                    percentageDifference);
              }
              return null;
            })
        .filter(Objects::nonNull)
        .toList();
  }

  private List<MissingData> findMissingData(
      String fundTicker,
      Map<LocalDate, BigDecimal> yahooValuesByDate,
      Map<LocalDate, BigDecimal> databaseValuesByDate) {

    return yahooValuesByDate.entrySet().stream()
        .filter(
            entry ->
                !databaseValuesByDate.containsKey(entry.getKey())
                    && entry.getValue().compareTo(ZERO) != 0)
        .map(entry -> new MissingData(fundTicker, entry.getKey(), entry.getValue()))
        .toList();
  }

  private List<OrphanedData> findOrphanedData(
      String fundTicker,
      Map<LocalDate, BigDecimal> yahooValuesByDate,
      Map<LocalDate, BigDecimal> databaseValuesByDate) {

    return databaseValuesByDate.keySet().stream()
        .filter(date -> !yahooValuesByDate.containsKey(date))
        .map(date -> new OrphanedData(fundTicker, date))
        .toList();
  }

  private void logSummary(LocalDate startDate, LocalDate endDate, List<TickerCheckResult> results) {
    StringBuilder summary = new StringBuilder();
    summary.append(
        String.format("Fund Value Integrity Check Summary (%s to %s):%n%n", startDate, endDate));

    summary.append(buildLatestDaySummary(endDate, results));
    summary.append("\n");
    summary.append(buildCrossProviderSummaryTable(startDate, endDate, results));
    summary.append("\n");
    summary.append(buildTrendSummary(startDate, endDate, results));

    List<Discrepancy> criticalIssues = collectCriticalIssues(results);
    List<Discrepancy> infoIssues = collectInfoIssues(results);

    if (criticalIssues.isEmpty()) {
      if (infoIssues.isEmpty()) {
        log.info("{}", summary);
      } else {
        summary.append(
            String.format(
                "%n%s Expected Yahoo discrepancies (%d):%n", INFO_MARK, infoIssues.size()));
        summary.append(
            String.format(
                "   (Yahoo often returns intra-day prices instead of actual EOD prices)%n"));
        appendIssuesSummary(summary, infoIssues, 3);
        log.info("{}", summary);
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
      log.error("{}", summary);
    }
  }

  private String buildLatestDaySummary(LocalDate latestDate, List<TickerCheckResult> results) {
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
      long criticalCount =
          latestDayIssues.stream().filter(d -> d.severity() == Severity.CRITICAL).count();
      long infoCount = latestDayIssues.stream().filter(d -> d.severity() == Severity.INFO).count();

      if (criticalCount > 0) {
        summary.append(
            String.format(
                "  %s %d critical issue(s) found - investigate before NAV calculation%n",
                CROSS_MARK, criticalCount));
        latestDayIssues.stream()
            .filter(d -> d.severity() == Severity.CRITICAL)
            .forEach(
                d ->
                    summary.append(
                        String.format(
                            "    • %s: %s=%.5f vs %s=%.5f (diff: %.4f%%)%n",
                            d.fundTicker(),
                            getAnchorName(d.comparisonDescription()),
                            d.anchorValue(),
                            getComparedName(d.comparisonDescription()),
                            d.comparedValue(),
                            d.percentageDifference())));
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

  private String getAnchorName(String comparisonDescription) {
    if (comparisonDescription.contains("Exchange vs EODHD")) {
      return "Exchange";
    } else if (comparisonDescription.contains("EODHD vs Yahoo")) {
      return "EODHD";
    }
    return "Anchor";
  }

  private String getComparedName(String comparisonDescription) {
    if (comparisonDescription.contains("Exchange vs EODHD")) {
      return "EODHD";
    } else if (comparisonDescription.contains("EODHD vs Yahoo")) {
      return "Yahoo";
    }
    return "Compared";
  }

  private String buildCrossProviderSummaryTable(
      LocalDate startDate, LocalDate endDate, List<TickerCheckResult> results) {
    StringBuilder table = new StringBuilder();
    table.append(String.format("Cross-Provider Comparison (%s to %s):%n", startDate, endDate));
    table.append(String.format("  Anchor hierarchy: Exchange (Xetra/Euronext) → EODHD → Yahoo%n"));
    table.append(
        String.format(
            "  Exchange vs EODHD: CRITICAL if differs | EODHD vs Yahoo: INFO (expected)%n%n"));
    table.append(formatCrossProviderHeader());
    table.append(formatCrossProviderSeparator());

    for (TickerCheckResult result : results) {
      String eodhdStatus = result.eodhdOk() ? CHECK_MARK : CROSS_MARK;

      String exchangeStatus;
      if (result.ticker().getXetraStorageKey().isPresent()
          || result.ticker().getEuronextParisStorageKey().isPresent()) {
        exchangeStatus = result.exchangeOk() ? CHECK_MARK : CROSS_MARK;
      } else {
        exchangeStatus = NOT_APPLICABLE;
      }

      String yahooStatus = result.yahooOk() ? CHECK_MARK : WARNING_MARK;

      table.append(
          formatCrossProviderRow(
              truncateFundName(result.ticker().getDisplayName()),
              eodhdStatus,
              exchangeStatus,
              yahooStatus));
    }
    table.append(formatCrossProviderFooter());

    return table.toString();
  }

  private String buildTrendSummary(
      LocalDate startDate, LocalDate endDate, List<TickerCheckResult> results) {
    StringBuilder summary = new StringBuilder();
    summary.append(String.format("30-Day Trend Summary:%n"));
    summary.append(formatTrendHeader());
    summary.append(formatTrendSeparator());

    for (TickerCheckResult result : results) {
      List<Discrepancy> criticalDiscrepancies =
          result.crossProviderDiscrepancies().stream()
              .filter(d -> d.severity() == Severity.CRITICAL)
              .toList();

      String status;
      String avgDiff;
      String maxDiff;
      String pattern;

      if (criticalDiscrepancies.isEmpty()) {
        status = CHECK_MARK;
        avgDiff = "-";
        maxDiff = "-";
        pattern = "";
      } else {
        status = CROSS_MARK;
        BigDecimal totalDiff =
            criticalDiscrepancies.stream()
                .map(Discrepancy::percentageDifference)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal avgDiffValue =
            totalDiff.divide(new BigDecimal(criticalDiscrepancies.size()), 4, HALF_UP);
        BigDecimal maxDiffValue =
            criticalDiscrepancies.stream()
                .map(Discrepancy::percentageDifference)
                .max(Comparator.naturalOrder())
                .orElse(ZERO);

        avgDiff = String.format("%.4f%%", avgDiffValue);
        maxDiff = String.format("%.4f%%", maxDiffValue);
        pattern =
            criticalDiscrepancies.size() > 25
                ? "Consistent"
                : criticalDiscrepancies.size() > 10 ? "Frequent" : "Intermittent";
      }

      summary.append(
          formatTrendRow(
              truncateFundName(result.ticker().getDisplayName()),
              status,
              avgDiff,
              maxDiff,
              pattern));
    }
    summary.append(formatTrendFooter());

    return summary.toString();
  }

  private List<Discrepancy> collectCriticalIssues(List<TickerCheckResult> results) {
    return results.stream()
        .flatMap(r -> r.crossProviderDiscrepancies().stream())
        .filter(d -> d.severity() == Severity.CRITICAL)
        .toList();
  }

  private List<Discrepancy> collectInfoIssues(List<TickerCheckResult> results) {
    return results.stream()
        .flatMap(r -> r.crossProviderDiscrepancies().stream())
        .filter(d -> d.severity() == Severity.INFO)
        .toList();
  }

  private void appendIssueDetails(StringBuilder summary, List<Discrepancy> issues) {
    issues.stream()
        .sorted(Comparator.comparing(Discrepancy::date).reversed())
        .limit(10)
        .forEach(
            d ->
                summary.append(
                    String.format(
                        "  • %s [%s]: %s %.5f vs %.5f, diff=%.5f (%.4f%%)%n",
                        d.fundTicker(),
                        d.date(),
                        d.comparisonDescription(),
                        d.anchorValue(),
                        d.comparedValue(),
                        d.difference(),
                        d.percentageDifference())));
    if (issues.size() > 10) {
      summary.append(String.format("  ... and %d more%n", issues.size() - 10));
    }
  }

  private void appendIssuesSummary(StringBuilder summary, List<Discrepancy> issues, int limit) {
    Map<String, Long> countByFund =
        issues.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    Discrepancy::fundTicker, java.util.stream.Collectors.counting()));

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

  private String truncateFundName(String name) {
    if (name.length() <= FUND_NAME_WIDTH) {
      return name;
    }
    return name.substring(0, FUND_NAME_WIDTH - 3) + "...";
  }

  private String formatCrossProviderHeader() {
    return String.format(
        "┌─%-"
            + FUND_NAME_WIDTH
            + "s─┬────────┬────────────┬────────┐%n"
            + "│ %-"
            + FUND_NAME_WIDTH
            + "s │ %-6s │ %-10s │ %-6s │%n",
        "─".repeat(FUND_NAME_WIDTH),
        "Fund",
        "EODHD",
        "Exchange",
        "Yahoo");
  }

  private String formatCrossProviderSeparator() {
    return String.format(
        "├─%-" + FUND_NAME_WIDTH + "s─┼────────┼────────────┼────────┤%n",
        "─".repeat(FUND_NAME_WIDTH));
  }

  private String formatCrossProviderRow(String fund, String eodhd, String exchange, String yahoo) {
    return String.format(
        "│ %-" + FUND_NAME_WIDTH + "s │ %s │ %s │ %s │%n",
        fund,
        padCrossProviderStatus(eodhd, 6),
        padCrossProviderStatus(exchange, 10),
        padCrossProviderStatus(yahoo, 6));
  }

  private String padCrossProviderStatus(String status, int width) {
    if (status.equals(CHECK_MARK)
        || status.equals(CROSS_MARK)
        || status.equals(WARNING_MARK)
        || status.equals(INFO_MARK)) {
      return status + " ".repeat(width - 1);
    }
    return String.format("%-" + width + "s", status);
  }

  private String formatCrossProviderFooter() {
    return String.format(
        "└─%-" + FUND_NAME_WIDTH + "s─┴────────┴────────────┴────────┘%n",
        "─".repeat(FUND_NAME_WIDTH));
  }

  private String formatTrendHeader() {
    return String.format(
        "┌─%-"
            + FUND_NAME_WIDTH
            + "s─┬────────┬──────────┬──────────┬─────────────┐%n"
            + "│ %-"
            + FUND_NAME_WIDTH
            + "s │ %-6s │ %-8s │ %-8s │ %-11s │%n",
        "─".repeat(FUND_NAME_WIDTH),
        "Fund",
        "Status",
        "Avg Δ",
        "Max Δ",
        "Pattern");
  }

  private String formatTrendSeparator() {
    return String.format(
        "├─%-" + FUND_NAME_WIDTH + "s─┼────────┼──────────┼──────────┼─────────────┤%n",
        "─".repeat(FUND_NAME_WIDTH));
  }

  private String formatTrendRow(
      String fund, String status, String avgDiff, String maxDiff, String pattern) {
    return String.format(
        "│ %-" + FUND_NAME_WIDTH + "s │ %s │ %-8s │ %-8s │ %-11s │%n",
        fund,
        padCrossProviderStatus(status, 6),
        avgDiff,
        maxDiff,
        pattern);
  }

  private String formatTrendFooter() {
    return String.format(
        "└─%-" + FUND_NAME_WIDTH + "s─┴────────┴──────────┴──────────┴─────────────┘%n",
        "─".repeat(FUND_NAME_WIDTH));
  }

  private BigDecimal calculatePercentageDifference(
      BigDecimal anchorValue, BigDecimal comparedValue) {
    if (anchorValue.compareTo(ZERO) == 0) {
      return comparedValue.compareTo(ZERO) == 0 ? ZERO : new BigDecimal("100");
    }

    return anchorValue
        .subtract(comparedValue)
        .abs()
        .multiply(new BigDecimal("100"))
        .divide(anchorValue.abs(), 4, HALF_UP);
  }

  private List<Discrepancy> findCrossProviderDiscrepancies(
      FundTicker ticker, LocalDate startDate, LocalDate endDate) {
    List<Discrepancy> discrepancies = new ArrayList<>();

    Map<LocalDate, BigDecimal> eodhdByDate =
        convertToDateValueMap(
            fundValueRepository.findValuesBetweenDates(
                ticker.getEodhdTicker(), startDate, endDate));
    Map<LocalDate, BigDecimal> yahooByDate =
        convertToDateValueMap(
            fundValueRepository.findValuesBetweenDates(
                ticker.getYahooTicker(), startDate, endDate));

    ticker
        .getXetraStorageKey()
        .ifPresent(
            xetraKey -> {
              Map<LocalDate, BigDecimal> xetraByDate =
                  convertToDateValueMap(
                      fundValueRepository.findValuesBetweenDates(xetraKey, startDate, endDate));
              discrepancies.addAll(
                  compareProviders(
                      ticker.getDisplayName(),
                      "Deutsche Börse",
                      xetraByDate,
                      "EODHD",
                      eodhdByDate,
                      Severity.CRITICAL,
                      "Exchange vs EODHD"));
            });

    ticker
        .getEuronextParisStorageKey()
        .ifPresent(
            euronextKey -> {
              Map<LocalDate, BigDecimal> euronextByDate =
                  convertToDateValueMap(
                      fundValueRepository.findValuesBetweenDates(euronextKey, startDate, endDate));
              discrepancies.addAll(
                  compareProviders(
                      ticker.getDisplayName(),
                      "Euronext",
                      euronextByDate,
                      "EODHD",
                      eodhdByDate,
                      Severity.CRITICAL,
                      "Exchange vs EODHD"));
            });

    discrepancies.addAll(
        compareProviders(
            ticker.getDisplayName(),
            "EODHD",
            eodhdByDate,
            "Yahoo",
            yahooByDate,
            Severity.INFO,
            "EODHD vs Yahoo"));

    return discrepancies;
  }

  private List<Discrepancy> compareProviders(
      String tickerName,
      String anchorProviderName,
      Map<LocalDate, BigDecimal> anchorByDate,
      String comparedProviderName,
      Map<LocalDate, BigDecimal> comparedByDate,
      Severity severity,
      String comparisonDescription) {

    return anchorByDate.entrySet().stream()
        .filter(entry -> comparedByDate.containsKey(entry.getKey()))
        .map(
            entry -> {
              LocalDate date = entry.getKey();
              BigDecimal anchorValue = entry.getValue().setScale(DATABASE_SCALE, HALF_UP);
              BigDecimal comparedValue = comparedByDate.get(date).setScale(DATABASE_SCALE, HALF_UP);

              BigDecimal percentageDiff = calculatePercentageDifference(anchorValue, comparedValue);
              if (percentageDiff.compareTo(CROSS_PROVIDER_THRESHOLD_PERCENT) > 0) {
                BigDecimal difference = anchorValue.subtract(comparedValue).abs();
                return new Discrepancy(
                    tickerName + " (" + anchorProviderName + " vs " + comparedProviderName + ")",
                    date,
                    anchorValue,
                    comparedValue,
                    difference,
                    percentageDiff,
                    severity,
                    comparisonDescription);
              }
              return null;
            })
        .filter(Objects::nonNull)
        .toList();
  }
}
