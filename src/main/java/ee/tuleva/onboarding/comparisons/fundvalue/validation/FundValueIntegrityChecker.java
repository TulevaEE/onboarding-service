package ee.tuleva.onboarding.comparisons.fundvalue.validation;

import static ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.Severity.CRITICAL;
import static ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.PriceSource;
import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.YahooFundValueRetriever;
import ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.Discrepancy;
import ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.MissingData;
import ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.OrphanedData;
import ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.Severity;
import ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.StaleSource;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
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
  private static final BigDecimal NAV_ROUNDING_THRESHOLD_PERCENT = new BigDecimal("0.1");
  static final int MAX_SOURCE_LAG_WORKING_DAYS = 3;
  private static final int MORNINGSTAR_SCALE = 2;
  private static final int EUFUND_SCALE = 3;
  private static final LocalDate CROSS_PROVIDER_CHECK_START_DATE = LocalDate.of(2026, 2, 11);
  private static final int FUND_NAME_WIDTH = 49;
  private static final String CHECK_MARK = "✅";
  private static final String CROSS_MARK = "❌";
  private static final String WARNING_MARK = "⚠️";
  private static final String INFO_MARK = "ℹ️";
  private static final String NOT_APPLICABLE = "-";

  private final YahooFundValueRetriever yahooFundValueRetriever;
  private final FundValueRepository fundValueRepository;
  private final PriorityPriceProvider priorityPriceProvider;
  private final PublicHolidays publicHolidays;
  private final Clock clock;
  private final OperationsNotificationService notificationService;

  record TickerCheckResult(
      FundTicker ticker,
      IntegrityCheckResult yahooVsDbResult,
      Set<String> configuredSources,
      Set<String> sourcesWithData,
      List<StaleSource> staleSources,
      List<Discrepancy> crossProviderDiscrepancies) {

    boolean hasYahooVsDbIssues() {
      return yahooVsDbResult != null && yahooVsDbResult.hasIssues();
    }

    boolean hasCrossProviderIssues() {
      return !crossProviderDiscrepancies.isEmpty();
    }

    boolean isSourceStale(String source) {
      return staleSources.stream().anyMatch(staleSource -> staleSource.source().equals(source));
    }

    boolean hasCriticalIssues() {
      return !staleSources.isEmpty()
          || crossProviderDiscrepancies.stream().anyMatch(d -> d.severity() == CRITICAL);
    }
  }

  @Scheduled(cron = "0 30 * * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "FundValueIntegrityChecker_performIntegrityCheck",
      lockAtMostFor = "55m",
      lockAtLeastFor = "5m")
  public void performIntegrityCheck() {
    runIntegrityCheck(LocalDate.now(clock).minusDays(1));
  }

  public String runIntegrityCheck(LocalDate endDate) {
    LocalDate crossProviderStartDate =
        endDate.minusDays(30).isBefore(CROSS_PROVIDER_CHECK_START_DATE)
            ? CROSS_PROVIDER_CHECK_START_DATE
            : endDate.minusDays(30);

    List<TickerCheckResult> results = collectAllResults(crossProviderStartDate, endDate);
    String summary = buildSummary(crossProviderStartDate, endDate, results);
    logSummary(summary, results);
    notifyIfCritical(results);
    return summary;
  }

  private List<TickerCheckResult> collectAllResults(LocalDate startDate, LocalDate endDate) {
    return Arrays.stream(FundTicker.values())
        .map(
            ticker -> {
              IntegrityCheckResult yahooVsDbResult =
                  verifyFundDataIntegrity(ticker.getYahooTicker(), startDate, endDate);
              List<SourceValues> sources = loadSources(ticker, startDate, endDate);

              return new TickerCheckResult(
                  ticker,
                  yahooVsDbResult,
                  tickerSources(ticker).stream().map(TickerSource::name).collect(toSet()),
                  sources.stream().map(values -> values.source().name()).collect(toSet()),
                  checkSourceFreshness(ticker, endDate),
                  crossProviderDiscrepancies(ticker, sources));
            })
        .toList();
  }

  List<Discrepancy> checkCrossProviderIntegrity(
      FundTicker ticker, LocalDate startDate, LocalDate endDate) {
    return crossProviderDiscrepancies(ticker, loadSources(ticker, startDate, endDate));
  }

  private record TickerSource(String name, String displayName, String storageKey, int scale) {}

  private record SourceValues(TickerSource source, Map<LocalDate, BigDecimal> valuesByDate) {}

  private List<TickerSource> tickerSources(FundTicker ticker) {
    return PriorityPriceProvider.priceFeeds().stream()
        .flatMap(
            feed ->
                feed.storageKey().apply(ticker).stream()
                    .map(storageKey -> tickerSource(feed.source(), storageKey)))
        .toList();
  }

  private TickerSource tickerSource(PriceSource source, String storageKey) {
    return switch (source) {
      case BLACKROCK -> new TickerSource("BlackRock", "BlackRock", storageKey, DATABASE_SCALE);
      case MORNINGSTAR ->
          new TickerSource("Morningstar", "Morningstar", storageKey, MORNINGSTAR_SCALE);
      case EODHD ->
          new TickerSource(
              "EODHD",
              "EODHD",
              storageKey,
              storageKey.endsWith(".EUFUND") ? EUFUND_SCALE : DATABASE_SCALE);
      case DEUTSCHE_BOERSE ->
          new TickerSource("Exchange", "Deutsche Börse", storageKey, DATABASE_SCALE);
      case EURONEXT -> new TickerSource("Exchange", "Euronext", storageKey, DATABASE_SCALE);
      case YAHOO -> new TickerSource("Yahoo", "Yahoo", storageKey, DATABASE_SCALE);
    };
  }

  private List<SourceValues> loadSources(
      FundTicker ticker, LocalDate startDate, LocalDate endDate) {
    return tickerSources(ticker).stream()
        .map(
            source ->
                new SourceValues(
                    source,
                    convertToDateValueMap(
                        fundValueRepository.findValuesBetweenDates(
                            source.storageKey(), startDate, endDate))))
        .filter(sourceValues -> !sourceValues.valuesByDate().isEmpty())
        .toList();
  }

  private List<Discrepancy> crossProviderDiscrepancies(
      FundTicker ticker, List<SourceValues> sources) {
    if (sources.size() < 2) {
      return List.of();
    }
    return allDates(sources).stream()
        .flatMap(date -> discrepanciesOnDate(ticker, sources, date).stream())
        .toList();
  }

  private SortedSet<LocalDate> allDates(List<SourceValues> sources) {
    return sources.stream()
        .flatMap(source -> source.valuesByDate().keySet().stream())
        .collect(toCollection(TreeSet::new));
  }

  private List<Discrepancy> discrepanciesOnDate(
      FundTicker ticker, List<SourceValues> sources, LocalDate date) {
    List<SourceValues> present =
        sources.stream().filter(source -> source.valuesByDate().containsKey(date)).toList();
    if (present.size() < 2) {
      return List.of();
    }
    SourceValues anchor = present.getFirst();
    return present.stream()
        .skip(1)
        .map(compared -> compareOnDate(ticker, date, anchor, compared))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<Discrepancy> compareOnDate(
      FundTicker ticker, LocalDate date, SourceValues anchor, SourceValues compared) {
    int scale = Math.min(anchor.source().scale(), compared.source().scale());
    BigDecimal thresholdPercent =
        scale < DATABASE_SCALE ? NAV_ROUNDING_THRESHOLD_PERCENT : CROSS_PROVIDER_THRESHOLD_PERCENT;
    BigDecimal anchorValue = anchor.valuesByDate().get(date).setScale(scale, HALF_UP);
    BigDecimal comparedValue = compared.valuesByDate().get(date).setScale(scale, HALF_UP);
    BigDecimal percentageDiff = calculatePercentageDifference(anchorValue, comparedValue);
    if (percentageDiff.compareTo(thresholdPercent) <= 0) {
      return Optional.empty();
    }
    Severity severity = compared.source().name().equals("Yahoo") ? INFO : CRITICAL;
    BigDecimal difference = anchorValue.subtract(comparedValue).abs();
    return Optional.of(
        new Discrepancy(
            ticker.getDisplayName()
                + " ("
                + anchor.source().displayName()
                + " vs "
                + compared.source().displayName()
                + ")",
            date,
            anchorValue,
            comparedValue,
            difference,
            percentageDiff,
            severity,
            anchor.source().name() + " vs " + compared.source().name()));
  }

  List<StaleSource> checkSourceFreshness(FundTicker ticker, LocalDate endDate) {
    return tickerSources(ticker).stream()
        .map(source -> staleSourceFor(ticker, source, endDate))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<StaleSource> staleSourceFor(
      FundTicker ticker, TickerSource source, LocalDate endDate) {
    return fundValueRepository
        .findLastValueForFund(source.storageKey())
        .map(FundValue::date)
        .map(
            lastDate ->
                new StaleSource(
                    ticker.getDisplayName(),
                    source.name(),
                    source.storageKey(),
                    lastDate,
                    publicHolidays.countWorkingDaysBehind(lastDate, endDate)))
        .filter(staleSource -> staleSource.workingDaysBehind() > MAX_SOURCE_LAG_WORKING_DAYS);
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
      log.warn("Skipping integrity check: fund={}, reason={}", fundTicker, e.getMessage());
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

  String buildSummary(LocalDate startDate, LocalDate endDate, List<TickerCheckResult> results) {
    StringBuilder summary = new StringBuilder();
    summary.append(
        String.format("Fund Value Integrity Check Summary (%s to %s):%n%n", startDate, endDate));

    summary.append(buildLatestDaySummary(endDate, results));
    summary.append(buildStaleSourcesSummary(results));
    summary.append("\n");
    summary.append(buildCrossProviderSummaryTable(startDate, endDate, results));

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

  void notifyIfCritical(List<TickerCheckResult> results) {
    boolean hasCriticalIssues = results.stream().anyMatch(TickerCheckResult::hasCriticalIssues);
    if (!hasCriticalIssues) {
      return;
    }
    try {
      notificationService.sendMessage(buildCriticalAlert(results), INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send fund value integrity critical alert", e);
    }
  }

  private String buildCriticalAlert(List<TickerCheckResult> results) {
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
                        "  PRICE DISCREPANCY: %s [%s] %s anchor=%s vs compared=%s (%s%%)%n",
                        d.fundTicker(),
                        d.date(),
                        d.comparisonDescription(),
                        d.anchorValue().toPlainString(),
                        d.comparedValue().toPlainString(),
                        d.percentageDifference().toPlainString())));
    return sb.toString().stripTrailing();
  }

  private void logSummary(String summary, List<TickerCheckResult> results) {
    boolean hasCriticalIssues = results.stream().anyMatch(TickerCheckResult::hasCriticalIssues);
    if (hasCriticalIssues) {
      log.error("{}", summary);
    } else {
      log.info("{}", summary);
    }
  }

  private String buildStaleSourcesSummary(List<TickerCheckResult> results) {
    List<StaleSource> staleSources =
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

  private String sourceStatus(TickerCheckResult result, String sourceName, LocalDate endDate) {
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

  private boolean hasDiscrepancyOn(TickerCheckResult result, LocalDate date, String sourceName) {
    return result.crossProviderDiscrepancies().stream()
        .anyMatch(d -> d.date().equals(date) && involves(d, sourceName));
  }

  private boolean hasCriticalDiscrepancyOn(
      TickerCheckResult result, LocalDate date, String sourceName) {
    return result.crossProviderDiscrepancies().stream()
        .anyMatch(
            d -> d.date().equals(date) && d.severity() == CRITICAL && involves(d, sourceName));
  }

  private boolean involves(Discrepancy discrepancy, String sourceName) {
    return Arrays.asList(discrepancy.comparisonDescription().split(" vs ")).contains(sourceName);
  }

  private String getAnchorName(String comparisonDescription) {
    return comparisonDescription.split(" vs ")[0];
  }

  private String getComparedName(String comparisonDescription) {
    return comparisonDescription.split(" vs ")[1];
  }

  private String buildCrossProviderSummaryTable(
      LocalDate startDate, LocalDate endDate, List<TickerCheckResult> results) {
    StringBuilder table = new StringBuilder();
    table.append(String.format("Cross-Provider Comparison (%s):%n", endDate));
    table.append(
        String.format(
            "  Each source is compared against the highest-priority source with data"
                + " — mismatch severity: vs Yahoo → INFO, all others → CRITICAL%n%n"));
    table.append(formatCrossProviderHeader());
    table.append(formatCrossProviderSeparator());

    for (TickerCheckResult result : results) {
      table.append(
          formatCrossProviderRow(
              truncateFundName(result.ticker().getDisplayName()),
              sourceStatus(result, "EODHD", endDate),
              sourceStatus(result, "Exchange", endDate),
              sourceStatus(result, "BlackRock", endDate),
              sourceStatus(result, "Morningstar", endDate),
              sourceStatus(result, "Yahoo", endDate),
              formatLastPrice(result.ticker(), endDate)));
    }
    table.append(formatCrossProviderFooter());

    return table.toString();
  }

  private List<Discrepancy> collectCriticalIssues(List<TickerCheckResult> results) {
    return results.stream()
        .flatMap(r -> r.crossProviderDiscrepancies().stream())
        .filter(d -> d.severity() == CRITICAL)
        .toList();
  }

  private List<Discrepancy> collectInfoIssues(List<TickerCheckResult> results) {
    return results.stream()
        .flatMap(r -> r.crossProviderDiscrepancies().stream())
        .filter(d -> d.severity() == INFO)
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

  private String formatLastPrice(FundTicker ticker, LocalDate endDate) {
    return priorityPriceProvider
        .resolve(ticker.getIsin(), endDate)
        .map(
            fundValue -> {
              long daysBehind = publicHolidays.countWorkingDaysBehind(fundValue.date(), endDate);
              String icon =
                  daysBehind == 0 ? CHECK_MARK : daysBehind == 1 ? WARNING_MARK : CROSS_MARK;
              return icon + " " + fundValue.date() + " " + fundValue.provider();
            })
        .orElse(CROSS_MARK + " no data");
  }

  private String formatCrossProviderHeader() {
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

  private String formatCrossProviderSeparator() {
    return String.format(
        "├─%-"
            + FUND_NAME_WIDTH
            + "s─┼────────┼────────────┼────────────┼──────────────┼────────┼───────────────────────────┤%n",
        "─".repeat(FUND_NAME_WIDTH));
  }

  private String formatCrossProviderRow(
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

  private String padCrossProviderStatus(String status, int width) {
    if (status.startsWith(CHECK_MARK)
        || status.startsWith(CROSS_MARK)
        || status.startsWith(WARNING_MARK)
        || status.startsWith(INFO_MARK)) {
      int padding = width - status.length();
      return padding > 0 ? status + " ".repeat(padding) : status;
    }
    return String.format("%-" + width + "s", status);
  }

  private String formatCrossProviderFooter() {
    return String.format(
        "└─%-"
            + FUND_NAME_WIDTH
            + "s─┴────────┴────────────┴────────────┴──────────────┴────────┴───────────────────────────┘%n",
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
}
