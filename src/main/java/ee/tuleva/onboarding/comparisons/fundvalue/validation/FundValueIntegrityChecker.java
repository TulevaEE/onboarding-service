package ee.tuleva.onboarding.comparisons.fundvalue.validation;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.NAVCheckValueRetriever;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundValueIntegrityChecker {

  private static final LocalDate EARLIEST_DATE = LocalDate.parse("2003-01-07");
  private static final int DATABASE_SCALE = 5;

  private final NAVCheckValueRetriever navCheckValueRetriever;
  private final FundValueRepository fundValueRepository;

  @Scheduled(cron = "0 30 * * * *", zone = "Europe/Tallinn")
  public void performIntegrityCheck() {
    LocalDate endDate = LocalDate.now();
    LocalDate startDate = EARLIEST_DATE;

    log.info("Starting fund value integrity check from {} to {}", startDate, endDate);

    for (String fundTicker : NAVCheckValueRetriever.FUND_TICKERS) {
      IntegrityCheckResult result = verifyFundDataIntegrity(fundTicker, startDate, endDate);
      logIntegrityCheckResult(result);
    }
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
    return navCheckValueRetriever.retrieveValuesForRange(startDate, endDate).stream()
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

              if (normalizedDbValue.compareTo(normalizedYahooValue) != 0) {
                BigDecimal difference = normalizedDbValue.subtract(normalizedYahooValue).abs();
                BigDecimal percentageDifference =
                    calculatePercentageDifference(normalizedDbValue, normalizedYahooValue);

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
}
