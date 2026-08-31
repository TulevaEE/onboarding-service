package ee.tuleva.onboarding.comparisons.fundvalue.validation;

import static ee.tuleva.onboarding.comparisons.fundvalue.validation.FundValueIntegrityReportFormatter.CHECK_MARK;
import static ee.tuleva.onboarding.comparisons.fundvalue.validation.FundValueIntegrityReportFormatter.CROSS_MARK;
import static ee.tuleva.onboarding.comparisons.fundvalue.validation.FundValueIntegrityReportFormatter.WARNING_MARK;
import static ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.Severity.CRITICAL;
import static ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.PriceSource;
import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.YahooFundValueRetriever;
import ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.Discrepancy;
import ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.MissingData;
import ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.OrphanedData;
import ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.Severity;
import ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.SourceValue;
import ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.StaleSource;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
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

  private final YahooFundValueRetriever yahooFundValueRetriever;
  private final FundValueRepository fundValueRepository;
  private final PriorityPriceProvider priorityPriceProvider;
  private final PublicHolidays publicHolidays;
  private final InstrumentReferenceService instrumentReferenceService;
  private final Clock clock;
  private final OperationsNotificationService notificationService;

  record InstrumentCheckResult(
      InstrumentReference instrument,
      Set<String> configuredSources,
      Set<String> sourcesWithData,
      List<StaleSource> staleSources,
      List<Discrepancy> crossProviderDiscrepancies) {

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

    List<InstrumentCheckResult> results = collectAllResults(crossProviderStartDate, endDate);
    String summary = buildSummary(crossProviderStartDate, endDate, results);
    logSummary(summary, results);
    notifyIfCritical(results);
    return summary;
  }

  private List<InstrumentCheckResult> collectAllResults(LocalDate startDate, LocalDate endDate) {
    return instrumentReferenceService.activeInstruments().stream()
        .map(
            instrument -> {
              List<SourceValues> sources = loadSources(instrument, startDate, endDate);

              return new InstrumentCheckResult(
                  instrument,
                  instrumentSources(instrument).stream()
                      .map(InstrumentSource::name)
                      .collect(toSet()),
                  sources.stream().map(values -> values.source().name()).collect(toSet()),
                  checkSourceFreshness(instrument, endDate),
                  crossProviderDiscrepancies(instrument, sources));
            })
        .toList();
  }

  List<Discrepancy> checkCrossProviderIntegrity(
      InstrumentReference instrument, LocalDate startDate, LocalDate endDate) {
    return crossProviderDiscrepancies(instrument, loadSources(instrument, startDate, endDate));
  }

  private List<InstrumentSource> instrumentSources(InstrumentReference instrument) {
    return PriorityPriceProvider.priceFeeds().stream()
        .flatMap(
            feed ->
                feed.storageKey().apply(instrument).stream()
                    .map(storageKey -> instrumentSource(feed.source(), storageKey)))
        .toList();
  }

  private InstrumentSource instrumentSource(PriceSource source, String storageKey) {
    return switch (source) {
      case BLACKROCK -> new InstrumentSource("BlackRock", "BlackRock", storageKey, DATABASE_SCALE);
      case MORNINGSTAR ->
          new InstrumentSource("Morningstar", "Morningstar", storageKey, MORNINGSTAR_SCALE);
      case EODHD ->
          new InstrumentSource(
              "EODHD",
              "EODHD",
              storageKey,
              storageKey.endsWith(".EUFUND") ? EUFUND_SCALE : DATABASE_SCALE);
      case DEUTSCHE_BOERSE ->
          new InstrumentSource("Exchange", "Deutsche Börse", storageKey, DATABASE_SCALE);
      case EURONEXT -> new InstrumentSource("Exchange", "Euronext", storageKey, DATABASE_SCALE);
      case YAHOO -> new InstrumentSource("Yahoo", "Yahoo", storageKey, DATABASE_SCALE);
    };
  }

  private List<SourceValues> loadSources(
      InstrumentReference instrument, LocalDate startDate, LocalDate endDate) {
    return instrumentSources(instrument).stream()
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
      InstrumentReference instrument, List<SourceValues> sources) {
    if (sources.size() < 2) {
      return List.of();
    }
    return allDates(sources).stream()
        .flatMap(date -> discrepanciesOnDate(instrument, sources, date).stream())
        .toList();
  }

  private SortedSet<LocalDate> allDates(List<SourceValues> sources) {
    return sources.stream()
        .flatMap(source -> source.valuesByDate().keySet().stream())
        .collect(toCollection(TreeSet::new));
  }

  private List<Discrepancy> discrepanciesOnDate(
      InstrumentReference instrument, List<SourceValues> sources, LocalDate date) {
    List<SourceValues> present =
        sources.stream().filter(source -> source.valuesByDate().containsKey(date)).toList();
    if (present.size() < 2) {
      return List.of();
    }
    List<SourceValue> allSourceValues =
        present.stream()
            .map(source -> new SourceValue(source.source().name(), source.valueOn(date)))
            .toList();
    SourceValues anchor = present.getFirst();
    return present.stream()
        .skip(1)
        .map(compared -> compareOnDate(instrument, date, anchor, compared, allSourceValues))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<Discrepancy> compareOnDate(
      InstrumentReference instrument,
      LocalDate date,
      SourceValues anchor,
      SourceValues compared,
      List<SourceValue> allSourceValues) {
    int scale = Math.min(anchor.source().scale(), compared.source().scale());
    BigDecimal thresholdPercent =
        scale < DATABASE_SCALE ? NAV_ROUNDING_THRESHOLD_PERCENT : CROSS_PROVIDER_THRESHOLD_PERCENT;
    BigDecimal anchorValue = anchor.valueOn(date).setScale(scale, HALF_UP);
    BigDecimal comparedValue = compared.valueOn(date).setScale(scale, HALF_UP);
    BigDecimal percentageDiff = calculatePercentageDifference(anchorValue, comparedValue);
    if (percentageDiff.compareTo(thresholdPercent) <= 0) {
      return Optional.empty();
    }
    Severity severity = compared.source().name().equals("Yahoo") ? INFO : CRITICAL;
    BigDecimal difference = anchorValue.subtract(comparedValue).abs();
    return Optional.of(
        new Discrepancy(
            instrument.getDisplayName()
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
            anchor.source().name() + " vs " + compared.source().name(),
            allSourceValues));
  }

  List<StaleSource> checkSourceFreshness(InstrumentReference instrument, LocalDate endDate) {
    return instrumentSources(instrument).stream()
        .map(source -> staleSourceFor(instrument, source, endDate))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<StaleSource> staleSourceFor(
      InstrumentReference instrument, InstrumentSource source, LocalDate endDate) {
    return fundValueRepository
        .findLastValueForFund(source.storageKey())
        .map(FundValue::date)
        .map(
            lastDate ->
                new StaleSource(
                    instrument.getDisplayName(),
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
              BigDecimal yahooValue =
                  requireNonNull(yahooValuesByDate.get(date), "Missing Yahoo value: date=" + date);

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

  String buildSummary(LocalDate startDate, LocalDate endDate, List<InstrumentCheckResult> results) {
    Map<InstrumentReference, String> lastPriceByInstrument =
        results.stream()
            .collect(
                toMap(
                    InstrumentCheckResult::instrument,
                    result -> formatLastPrice(result.instrument(), endDate)));
    return FundValueIntegrityReportFormatter.buildSummary(
        startDate, endDate, results, lastPriceByInstrument);
  }

  void notifyIfCritical(List<InstrumentCheckResult> results) {
    boolean hasCriticalIssues = results.stream().anyMatch(InstrumentCheckResult::hasCriticalIssues);
    if (!hasCriticalIssues) {
      return;
    }
    try {
      notificationService.sendMessage(
          FundValueIntegrityReportFormatter.buildCriticalAlert(results), INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send fund value integrity critical alert", e);
    }
  }

  private void logSummary(String summary, List<InstrumentCheckResult> results) {
    boolean hasCriticalIssues = results.stream().anyMatch(InstrumentCheckResult::hasCriticalIssues);
    if (hasCriticalIssues) {
      log.error("{}", summary);
    } else {
      log.info("{}", summary);
    }
  }

  private String formatLastPrice(InstrumentReference instrument, LocalDate endDate) {
    return priorityPriceProvider
        .resolve(instrument.getIsin(), endDate)
        .map(
            fundValue -> {
              long daysBehind = publicHolidays.countWorkingDaysBehind(fundValue.date(), endDate);
              String icon =
                  daysBehind == 0 ? CHECK_MARK : daysBehind == 1 ? WARNING_MARK : CROSS_MARK;
              return icon + " " + fundValue.date() + " " + fundValue.provider();
            })
        .orElse(CROSS_MARK + " no data");
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
