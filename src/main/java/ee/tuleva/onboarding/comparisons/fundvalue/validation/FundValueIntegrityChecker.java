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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundValueIntegrityChecker {

  private static final LocalDate EARLIEST_DATE = LocalDate.parse("2003-01-07");
  private static final int DATABASE_SCALE = 5;
  private static final BigDecimal SAME_PROVIDER_THRESHOLD_PERCENT = new BigDecimal("0.0001");
  private static final BigDecimal CROSS_PROVIDER_THRESHOLD_PERCENT = new BigDecimal("0.001");

  private final YahooFundValueRetriever yahooFundValueRetriever;
  private final FundValueRepository fundValueRepository;

  @Scheduled(cron = "0 30 * * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "FundValueIntegrityChecker_performIntegrityCheck",
      lockAtMostFor = "55m",
      lockAtLeastFor = "5m")
  public void performIntegrityCheck() {
    LocalDate endDate = LocalDate.now().minusDays(1);

    checkYahooVsDatabaseIntegrity(endDate);
    checkCrossProviderIntegrity(endDate);
  }

  private void checkYahooVsDatabaseIntegrity(LocalDate endDate) {
    log.info("Starting Yahoo vs database integrity check from {} to {}", EARLIEST_DATE, endDate);

    for (String fundTicker : FundTicker.getYahooTickers()) {
      checkYahooVsDatabaseIntegrity(fundTicker, EARLIEST_DATE, endDate);
    }
  }

  void checkYahooVsDatabaseIntegrity(String fundTicker, LocalDate startDate, LocalDate endDate) {
    IntegrityCheckResult result = verifyFundDataIntegrity(fundTicker, startDate, endDate);
    logIntegrityCheckResult(result);
  }

  private void checkCrossProviderIntegrity(LocalDate endDate) {
    LocalDate startDate = endDate.minusDays(30);

    log.info("Starting cross-provider integrity check from {} to {}", startDate, endDate);

    for (FundTicker ticker : FundTicker.values()) {
      checkCrossProviderIntegrity(ticker, startDate, endDate);
    }
  }

  List<Discrepancy> checkCrossProviderIntegrity(
      FundTicker ticker, LocalDate startDate, LocalDate endDate) {
    List<Discrepancy> discrepancies = findCrossProviderDiscrepancies(ticker, startDate, endDate);
    logCrossProviderDiscrepancies(ticker, discrepancies);
    return discrepancies;
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

  private void logIntegrityCheckResult(IntegrityCheckResult result) {
    for (Discrepancy discrepancy : result.getDiscrepancies()) {
      log.error(
          "DATA INTEGRITY ISSUE: Fund {} on {} - DB value: {}, Yahoo value: {}, "
              + "Difference: {} ({} %)",
          discrepancy.fundTicker(),
          discrepancy.date(),
          discrepancy.dbValue(),
          discrepancy.yahooValue(),
          discrepancy.difference(),
          discrepancy.percentageDifference());
    }

    for (MissingData missing : result.getMissingData()) {
      log.error(
          "MISSING DATA: Fund {} on {} exists in Yahoo Finance (value: {}) but not in database",
          missing.fundTicker(),
          missing.date(),
          missing.yahooValue());
    }

    for (OrphanedData orphaned : result.getOrphanedData()) {
      log.warn(
          "ORPHANED DATA: Fund {} on {} exists in database but not in Yahoo Finance response",
          orphaned.fundTicker(),
          orphaned.date());
    }
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
    List<FundValue> yahooValues =
        fundValueRepository.findValuesBetweenDates(ticker.getYahooTicker(), startDate, endDate);
    List<FundValue> eodhdValues =
        fundValueRepository.findValuesBetweenDates(ticker.getEodhdTicker(), startDate, endDate);

    Map<LocalDate, BigDecimal> yahooByDate = convertToDateValueMap(yahooValues);
    Map<LocalDate, BigDecimal> eodhdByDate = convertToDateValueMap(eodhdValues);

    return yahooByDate.entrySet().stream()
        .filter(entry -> eodhdByDate.containsKey(entry.getKey()))
        .map(
            entry -> {
              LocalDate date = entry.getKey();
              BigDecimal yahooValue = entry.getValue().setScale(DATABASE_SCALE, HALF_UP);
              BigDecimal eodhdValue = eodhdByDate.get(date).setScale(DATABASE_SCALE, HALF_UP);

              BigDecimal percentageDiff = calculatePercentageDifference(yahooValue, eodhdValue);
              if (percentageDiff.compareTo(CROSS_PROVIDER_THRESHOLD_PERCENT) > 0) {
                BigDecimal difference = yahooValue.subtract(eodhdValue).abs();
                return new Discrepancy(
                    ticker.getDisplayName(),
                    date,
                    yahooValue,
                    eodhdValue,
                    difference,
                    percentageDiff);
              }
              return null;
            })
        .filter(Objects::nonNull)
        .toList();
  }

  private void logCrossProviderDiscrepancies(FundTicker ticker, List<Discrepancy> discrepancies) {
    for (Discrepancy discrepancy : discrepancies) {
      log.error(
          "CROSS-PROVIDER DISCREPANCY: {} on {} - Yahoo: {}, EODHD: {}, Difference: {} ({}%)",
          ticker.getDisplayName(),
          discrepancy.date(),
          discrepancy.dbValue(),
          discrepancy.yahooValue(),
          discrepancy.difference(),
          discrepancy.percentageDifference());
    }
  }
}
