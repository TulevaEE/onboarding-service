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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
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
  private static final String NOT_APPLICABLE = "-";

  private final YahooFundValueRetriever yahooFundValueRetriever;
  private final FundValueRepository fundValueRepository;

  record TickerCheckResult(
      FundTicker ticker,
      IntegrityCheckResult yahooVsDbResult,
      boolean yahooOk,
      boolean eodhdOk,
      boolean xetraOk,
      boolean euronextOk,
      List<Discrepancy> crossProviderDiscrepancies) {

    boolean hasYahooVsDbIssues() {
      return yahooVsDbResult != null && yahooVsDbResult.hasIssues();
    }

    boolean hasCrossProviderIssues() {
      return !crossProviderDiscrepancies.isEmpty();
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
                  crossProviderResult.yahooOk(),
                  crossProviderResult.eodhdOk(),
                  crossProviderResult.xetraOk(),
                  crossProviderResult.euronextOk(),
                  crossProviderResult.discrepancies());
            })
        .toList();
  }

  private record CrossProviderCheckResult(
      boolean yahooOk,
      boolean eodhdOk,
      boolean xetraOk,
      boolean euronextOk,
      List<Discrepancy> discrepancies) {}

  void checkYahooVsDatabaseIntegrity(String fundTicker, LocalDate startDate, LocalDate endDate) {
    verifyFundDataIntegrity(fundTicker, startDate, endDate);
  }

  List<Discrepancy> checkCrossProviderIntegrity(
      FundTicker ticker, LocalDate startDate, LocalDate endDate) {
    return findCrossProviderDiscrepancies(ticker, startDate, endDate);
  }

  private CrossProviderCheckResult checkCrossProviderIntegrityInternal(
      FundTicker ticker, LocalDate startDate, LocalDate endDate) {
    Map<LocalDate, BigDecimal> yahooByDate =
        convertToDateValueMap(
            fundValueRepository.findValuesBetweenDates(
                ticker.getYahooTicker(), startDate, endDate));
    Map<LocalDate, BigDecimal> eodhdByDate =
        convertToDateValueMap(
            fundValueRepository.findValuesBetweenDates(
                ticker.getEodhdTicker(), startDate, endDate));

    List<Discrepancy> discrepancies = new ArrayList<>();
    List<Discrepancy> yahooVsEodhdDiscrepancies =
        compareProviders(ticker.getDisplayName(), "Yahoo", yahooByDate, "EODHD", eodhdByDate);
    discrepancies.addAll(yahooVsEodhdDiscrepancies);

    boolean yahooOk = !yahooByDate.isEmpty();
    boolean eodhdOk = yahooVsEodhdDiscrepancies.isEmpty() && !eodhdByDate.isEmpty();

    boolean xetraOk = true;
    Optional<String> xetraKey = ticker.getXetraStorageKey();
    if (xetraKey.isPresent()) {
      Map<LocalDate, BigDecimal> xetraByDate =
          convertToDateValueMap(
              fundValueRepository.findValuesBetweenDates(xetraKey.get(), startDate, endDate));
      List<Discrepancy> xetraDiscrepancies =
          compareProviders(
              ticker.getDisplayName(), "Yahoo", yahooByDate, "Deutsche Börse", xetraByDate);
      discrepancies.addAll(xetraDiscrepancies);
      xetraOk = xetraDiscrepancies.isEmpty() && !xetraByDate.isEmpty();
    }

    boolean euronextOk = true;
    Optional<String> euronextKey = ticker.getEuronextParisStorageKey();
    if (euronextKey.isPresent()) {
      Map<LocalDate, BigDecimal> euronextByDate =
          convertToDateValueMap(
              fundValueRepository.findValuesBetweenDates(euronextKey.get(), startDate, endDate));
      List<Discrepancy> euronextDiscrepancies =
          compareProviders(
              ticker.getDisplayName(), "Yahoo", yahooByDate, "Euronext", euronextByDate);
      discrepancies.addAll(euronextDiscrepancies);
      euronextOk = euronextDiscrepancies.isEmpty() && !euronextByDate.isEmpty();
    }

    return new CrossProviderCheckResult(yahooOk, eodhdOk, xetraOk, euronextOk, discrepancies);
  }

  private IntegrityCheckResult verifyFundDataIntegrity(
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

    summary.append(buildYahooVsDbSummaryTable(startDate, endDate, results));
    summary.append("\n");
    summary.append(buildCrossProviderSummaryTable(startDate, endDate, results));

    List<String> issues = collectIssueDetails(results);

    if (issues.isEmpty()) {
      log.info("{}", summary);
    } else {
      summary.append(String.format("%nIssues found (%d):%n", issues.size()));
      issues.forEach(issue -> summary.append(String.format("  %s %s%n", CROSS_MARK, issue)));
      log.error("{}", summary);
    }
  }

  private String buildYahooVsDbSummaryTable(
      LocalDate startDate, LocalDate endDate, List<TickerCheckResult> results) {
    StringBuilder table = new StringBuilder();
    table.append(String.format("Yahoo vs Database (%s to %s):%n", startDate, endDate));
    table.append(formatTableHeader("Fund", "Status", "Missing", "Orphaned"));
    table.append(formatTableSeparator());

    for (TickerCheckResult result : results) {
      IntegrityCheckResult yahooResult = result.yahooVsDbResult();
      String status = yahooResult.hasIssues() ? CROSS_MARK : CHECK_MARK;
      int missing = yahooResult.getMissingData().size();
      int orphaned = yahooResult.getOrphanedData().size();

      table.append(
          formatTableRow(
              truncateFundName(result.ticker().getDisplayName()),
              status,
              String.valueOf(missing),
              String.valueOf(orphaned)));
    }
    table.append(formatTableFooter());

    return table.toString();
  }

  private String buildCrossProviderSummaryTable(
      LocalDate startDate, LocalDate endDate, List<TickerCheckResult> results) {
    StringBuilder table = new StringBuilder();
    table.append(String.format("Cross-Provider Comparison (%s to %s):%n", startDate, endDate));
    table.append(formatCrossProviderHeader());
    table.append(formatCrossProviderSeparator());

    for (TickerCheckResult result : results) {
      String yahooStatus = result.yahooOk() ? CHECK_MARK : CROSS_MARK;
      String eodhdStatus = result.eodhdOk() ? CHECK_MARK : CROSS_MARK;
      String xetraStatus =
          result.ticker().getXetraStorageKey().isPresent()
              ? (result.xetraOk() ? CHECK_MARK : CROSS_MARK)
              : NOT_APPLICABLE;
      String euronextStatus =
          result.ticker().getEuronextParisStorageKey().isPresent()
              ? (result.euronextOk() ? CHECK_MARK : CROSS_MARK)
              : NOT_APPLICABLE;

      table.append(
          formatCrossProviderRow(
              truncateFundName(result.ticker().getDisplayName()),
              yahooStatus,
              eodhdStatus,
              xetraStatus,
              euronextStatus));
    }
    table.append(formatCrossProviderFooter());

    return table.toString();
  }

  private List<String> collectIssueDetails(List<TickerCheckResult> results) {
    List<String> issues = new ArrayList<>();

    for (TickerCheckResult result : results) {
      IntegrityCheckResult yahooResult = result.yahooVsDbResult();
      String fundName = result.ticker().getDisplayName();

      if (!yahooResult.getMissingData().isEmpty()) {
        List<LocalDate> missingDates =
            yahooResult.getMissingData().stream().map(MissingData::date).sorted().limit(5).toList();
        String datesStr =
            missingDates.size() < yahooResult.getMissingData().size()
                ? formatDates(missingDates) + " ..."
                : formatDates(missingDates);
        issues.add(
            String.format(
                "%s: Missing %d dates in database (%s)",
                fundName, yahooResult.getMissingData().size(), datesStr));
      }

      if (!yahooResult.getOrphanedData().isEmpty()) {
        List<LocalDate> orphanedDates =
            yahooResult.getOrphanedData().stream()
                .map(OrphanedData::date)
                .sorted()
                .limit(5)
                .toList();
        String datesStr =
            orphanedDates.size() < yahooResult.getOrphanedData().size()
                ? formatDates(orphanedDates) + " ..."
                : formatDates(orphanedDates);
        issues.add(
            String.format(
                "%s: %d orphaned dates in database (%s)",
                fundName, yahooResult.getOrphanedData().size(), datesStr));
      }

      for (Discrepancy discrepancy : result.crossProviderDiscrepancies()) {
        issues.add(
            String.format(
                "%s: %s values %.2f vs %.2f, diff=%.2f (%.4f%%)",
                discrepancy.fundTicker(),
                discrepancy.date(),
                discrepancy.dbValue(),
                discrepancy.yahooValue(),
                discrepancy.difference(),
                discrepancy.percentageDifference()));
      }
    }

    return issues;
  }

  private String truncateFundName(String name) {
    if (name.length() <= FUND_NAME_WIDTH) {
      return name;
    }
    return name.substring(0, FUND_NAME_WIDTH - 3) + "...";
  }

  private String formatDates(List<LocalDate> dates) {
    return dates.stream().map(LocalDate::toString).reduce((a, b) -> a + ", " + b).orElse("");
  }

  private String formatTableHeader(String col1, String col2, String col3, String col4) {
    return String.format(
        "┌─%-"
            + FUND_NAME_WIDTH
            + "s─┬────────┬─────────┬──────────┐%n"
            + "│ %-"
            + FUND_NAME_WIDTH
            + "s │ %-6s │ %-7s │ %-8s │%n",
        "─".repeat(FUND_NAME_WIDTH),
        col1,
        col2,
        col3,
        col4);
  }

  private String formatTableSeparator() {
    return String.format(
        "├─%-" + FUND_NAME_WIDTH + "s─┼────────┼─────────┼──────────┤%n",
        "─".repeat(FUND_NAME_WIDTH));
  }

  private String formatTableRow(String col1, String col2, String col3, String col4) {
    return String.format(
        "│ %-" + FUND_NAME_WIDTH + "s │ %s │ %-7s │ %-8s │%n", col1, padStatus(col2), col3, col4);
  }

  private String padStatus(String status) {
    if (status.equals(CHECK_MARK) || status.equals(CROSS_MARK)) {
      return status + "     ";
    }
    return String.format("%-6s", status);
  }

  private String formatTableFooter() {
    return String.format(
        "└─%-" + FUND_NAME_WIDTH + "s─┴────────┴─────────┴──────────┘%n",
        "─".repeat(FUND_NAME_WIDTH));
  }

  private String formatCrossProviderHeader() {
    return String.format(
        "┌─%-"
            + FUND_NAME_WIDTH
            + "s─┬────────┬────────┬─────────────────┬──────────┐%n"
            + "│ %-"
            + FUND_NAME_WIDTH
            + "s │ %-6s │ %-6s │ %-15s │ %-8s │%n",
        "─".repeat(FUND_NAME_WIDTH),
        "Fund",
        "Yahoo",
        "EODHD",
        "Deutsche Börse",
        "Euronext");
  }

  private String formatCrossProviderSeparator() {
    return String.format(
        "├─%-" + FUND_NAME_WIDTH + "s─┼────────┼────────┼─────────────────┼──────────┤%n",
        "─".repeat(FUND_NAME_WIDTH));
  }

  private String formatCrossProviderRow(
      String fund, String yahoo, String eodhd, String xetra, String euronext) {
    return String.format(
        "│ %-" + FUND_NAME_WIDTH + "s │ %s │ %s │ %s │ %s │%n",
        fund,
        padCrossProviderStatus(yahoo, 6),
        padCrossProviderStatus(eodhd, 6),
        padCrossProviderStatus(xetra, 15),
        padCrossProviderStatus(euronext, 8));
  }

  private String padCrossProviderStatus(String status, int width) {
    if (status.equals(CHECK_MARK) || status.equals(CROSS_MARK)) {
      return status + " ".repeat(width - 1);
    }
    return String.format("%-" + width + "s", status);
  }

  private String formatCrossProviderFooter() {
    return String.format(
        "└─%-" + FUND_NAME_WIDTH + "s─┴────────┴────────┴─────────────────┴──────────┘%n",
        "─".repeat(FUND_NAME_WIDTH));
  }

  private BigDecimal calculatePercentageDifference(
      BigDecimal databaseValue, BigDecimal yahooValue) {
    if (yahooValue.compareTo(ZERO) == 0) {
      return databaseValue.compareTo(ZERO) == 0 ? ZERO : new BigDecimal("100");
    }

    return databaseValue
        .subtract(yahooValue)
        .abs()
        .multiply(new BigDecimal("100"))
        .divide(yahooValue.abs(), 4, HALF_UP);
  }

  private List<Discrepancy> findCrossProviderDiscrepancies(
      FundTicker ticker, LocalDate startDate, LocalDate endDate) {
    List<Discrepancy> discrepancies = new ArrayList<>();

    Map<LocalDate, BigDecimal> yahooByDate =
        convertToDateValueMap(
            fundValueRepository.findValuesBetweenDates(
                ticker.getYahooTicker(), startDate, endDate));
    Map<LocalDate, BigDecimal> eodhdByDate =
        convertToDateValueMap(
            fundValueRepository.findValuesBetweenDates(
                ticker.getEodhdTicker(), startDate, endDate));

    discrepancies.addAll(
        compareProviders(ticker.getDisplayName(), "Yahoo", yahooByDate, "EODHD", eodhdByDate));

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
                      "Yahoo",
                      yahooByDate,
                      "Deutsche Börse",
                      xetraByDate));
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
                      ticker.getDisplayName(), "Yahoo", yahooByDate, "Euronext", euronextByDate));
            });

    return discrepancies;
  }

  private List<Discrepancy> compareProviders(
      String tickerName,
      String provider1Name,
      Map<LocalDate, BigDecimal> provider1ByDate,
      String provider2Name,
      Map<LocalDate, BigDecimal> provider2ByDate) {

    return provider1ByDate.entrySet().stream()
        .filter(entry -> provider2ByDate.containsKey(entry.getKey()))
        .map(
            entry -> {
              LocalDate date = entry.getKey();
              BigDecimal value1 = entry.getValue().setScale(DATABASE_SCALE, HALF_UP);
              BigDecimal value2 = provider2ByDate.get(date).setScale(DATABASE_SCALE, HALF_UP);

              BigDecimal percentageDiff = calculatePercentageDifference(value1, value2);
              if (percentageDiff.compareTo(CROSS_PROVIDER_THRESHOLD_PERCENT) > 0) {
                BigDecimal difference = value1.subtract(value2).abs();
                return new Discrepancy(
                    tickerName + " (" + provider1Name + " vs " + provider2Name + ")",
                    date,
                    value1,
                    value2,
                    difference,
                    percentageDiff);
              }
              return null;
            })
        .filter(Objects::nonNull)
        .toList();
  }
}
