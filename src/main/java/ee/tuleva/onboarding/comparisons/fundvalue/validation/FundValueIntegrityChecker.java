package ee.tuleva.onboarding.comparisons.fundvalue.validation;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.NAVCheckValueRetriever;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundValueIntegrityChecker {

  private static final LocalDate EARLIEST_DATE = LocalDate.parse("2003-01-07");

  private final NAVCheckValueRetriever navCheckValueRetriever;
  private final FundValueRepository fundValueRepository;

  @Scheduled(cron = "0 30 * * * *", zone = "Europe/Tallinn")
  public void performIntegrityCheck() {
    LocalDate endDate = LocalDate.now();
    LocalDate startDate = EARLIEST_DATE;

    log.info("Starting fund value integrity check from {} to {}", startDate, endDate);

    for (String fundTicker : NAVCheckValueRetriever.FUND_TICKERS) {
      verifyFundDataIntegrity(fundTicker, startDate, endDate);
    }
  }

  public void verifyFundDataIntegrity(String fundTicker, LocalDate startDate, LocalDate endDate) {
    try {
      List<FundValue> yahooFinanceValues = fetchYahooFinanceData(fundTicker, startDate, endDate);
      List<FundValue> databaseFundValues = fetchDatabaseData(fundTicker, startDate, endDate);

      Map<LocalDate, BigDecimal> yahooValuesByDate = convertToDateValueMap(yahooFinanceValues);
      Map<LocalDate, BigDecimal> databaseValuesByDate = convertToDateValueMap(databaseFundValues);

      compareAndReportDiscrepancies(fundTicker, yahooValuesByDate, databaseValuesByDate);
      checkForMissingDates(fundTicker, yahooValuesByDate, databaseValuesByDate);

    } catch (Exception e) {
      log.error("Failed to verify data integrity for fund {}: {}", fundTicker, e.getMessage(), e);
    }
  }

  private List<FundValue> fetchYahooFinanceData(String fundTicker, LocalDate startDate, LocalDate endDate) {
    return navCheckValueRetriever.retrieveValuesForRange(startDate, endDate)
        .stream()
        .filter(fundValue -> fundValue.key().equals(fundTicker))
        .toList();
  }

  private List<FundValue> fetchDatabaseData(String fundTicker, LocalDate startDate, LocalDate endDate) {
    return fundValueRepository.findValuesBetweenDates(fundTicker, startDate, endDate);
  }

  private Map<LocalDate, BigDecimal> convertToDateValueMap(List<FundValue> fundValues) {
    return fundValues.stream()
        .collect(toMap(
            FundValue::date,
            FundValue::value,
            (existingValue, duplicateValue) -> existingValue
        ));
  }

  private void compareAndReportDiscrepancies(
      String fundTicker,
      Map<LocalDate, BigDecimal> yahooValuesByDate,
      Map<LocalDate, BigDecimal> databaseValuesByDate) {

    for (Map.Entry<LocalDate, BigDecimal> databaseEntry : databaseValuesByDate.entrySet()) {
      LocalDate date = databaseEntry.getKey();
      BigDecimal databaseValue = databaseEntry.getValue();

      if (yahooValuesByDate.containsKey(date)) {
        BigDecimal yahooValue = yahooValuesByDate.get(date);

        if (databaseValue.compareTo(yahooValue) != 0) {
          BigDecimal difference = databaseValue.subtract(yahooValue).abs();
          BigDecimal percentageDifference = calculatePercentageDifference(databaseValue, yahooValue);

          log.error(
              "DATA INTEGRITY ISSUE: Fund {} on {} - DB value: {}, Yahoo value: {}, " +
              "Difference: {} ({} %)",
              fundTicker, date, databaseValue, yahooValue, difference, percentageDifference
          );
        }
      }
    }
  }

  private void checkForMissingDates(
      String fundTicker,
      Map<LocalDate, BigDecimal> yahooValuesByDate,
      Map<LocalDate, BigDecimal> databaseValuesByDate) {

    for (LocalDate yahooDate : yahooValuesByDate.keySet()) {
      if (!databaseValuesByDate.containsKey(yahooDate)) {
        BigDecimal yahooValue = yahooValuesByDate.get(yahooDate);
        if (yahooValue.compareTo(ZERO) != 0) {
          log.error(
              "MISSING DATA: Fund {} on {} exists in Yahoo Finance (value: {}) but not in database",
              fundTicker, yahooDate, yahooValue
          );
        }
      }
    }

    for (LocalDate databaseDate : databaseValuesByDate.keySet()) {
      if (!yahooValuesByDate.containsKey(databaseDate)) {
        log.warn(
            "ORPHANED DATA: Fund {} on {} exists in database but not in Yahoo Finance response",
            fundTicker, databaseDate
        );
      }
    }
  }

  private BigDecimal calculatePercentageDifference(BigDecimal databaseValue, BigDecimal yahooValue) {
    if (yahooValue.compareTo(ZERO) == 0) {
      return databaseValue.compareTo(ZERO) == 0 ? ZERO : new BigDecimal("100");
    }

    return databaseValue.subtract(yahooValue)
        .abs()
        .multiply(new BigDecimal("100"))
        .divide(yahooValue.abs(), 4, HALF_UP);
  }
}