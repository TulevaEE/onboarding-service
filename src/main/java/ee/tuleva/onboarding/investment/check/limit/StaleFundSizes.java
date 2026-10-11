package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.CheckType.OWNERSHIP;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.check.limit.OwnershipCheckRun.Result;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class StaleFundSizes {

  static final String HOLDINGS = "holdings";
  static final String STALE_SIZES = "staleSizes";
  private static final String ISIN = "isin";
  private static final String REPORTED_FUND_SIZE = "reportedFundSize";
  private static final String UNCHANGED_SINCE = "unchangedSince";

  private final LimitCheckEventRepository limitCheckEventRepository;

  Result comparedWithTheMonthBefore(Result result) {
    try {
      return result.withStaleSizes(
          unchangedSinceTheMonthBefore(result.fund(), result.checkDate(), result.holdings()));
    } catch (Exception e) {
      log.error(
          "Fund sizes not compared with the month before: fund={}, checkDate={}",
          result.fund(),
          result.checkDate(),
          e);
      return result.withSizesNotComparedBecause(OwnershipLimitCheckService.describe(e));
    }
  }

  private List<StaleFundSize> unchangedSinceTheMonthBefore(
      TulevaFund fund, LocalDate checkDate, List<OwnershipBreach> holdings) {
    var monthBefore = YearMonth.from(checkDate).minusMonths(1);
    return limitCheckEventRepository
        .findByFundAndCheckTypeAndCheckDateBetween(
            fund, OWNERSHIP, monthBefore.atDay(1), monthBefore.atEndOfMonth())
        .stream()
        .max(comparing(LimitCheckEvent::getCheckDate))
        .map(previous -> sizesUnchangedSince(previous, holdings))
        .orElse(List.of());
  }

  private static List<StaleFundSize> sizesUnchangedSince(
      LimitCheckEvent previous, List<OwnershipBreach> holdings) {
    var previousSizes = previousSizes(previous);
    var alreadyStaleSince = alreadyStaleSince(previous);
    return holdings.stream()
        .filter(
            holding -> sameFigure(previousSizes.get(holding.isin()), holding.reportedFundSize()))
        .map(
            holding ->
                new StaleFundSize(
                    holding.isin(),
                    holding.name(),
                    holding.reportedFundSize(),
                    holding.reportedCurrency(),
                    alreadyStaleSince.getOrDefault(holding.isin(), previous.getCheckDate())))
        .toList();
  }

  private static boolean sameFigure(@Nullable Number stored, BigDecimal reported) {
    return stored != null && stored.doubleValue() == reported.doubleValue();
  }

  private static Map<String, Number> previousSizes(LimitCheckEvent event) {
    return stored(event, HOLDINGS)
        .filter(
            holding ->
                holding.get(ISIN) instanceof String
                    && holding.get(REPORTED_FUND_SIZE) instanceof Number)
        .collect(
            toMap(
                holding -> (String) holding.get(ISIN),
                holding -> (Number) holding.get(REPORTED_FUND_SIZE),
                (first, second) -> first));
  }

  private static Map<String, LocalDate> alreadyStaleSince(LimitCheckEvent event) {
    return stored(event, STALE_SIZES)
        .filter(
            stale ->
                stale.get(ISIN) instanceof String && stale.get(UNCHANGED_SINCE) instanceof String)
        .collect(
            toMap(
                stale -> (String) stale.get(ISIN),
                stale -> LocalDate.parse((String) stale.get(UNCHANGED_SINCE)),
                (first, second) -> first));
  }

  private static Stream<Map<?, ?>> stored(LimitCheckEvent event, String key) {
    return event.getResult().get(key) instanceof List<?> entries
        ? entries.stream().filter(Map.class::isInstance).map(entry -> (Map<?, ?>) entry)
        : Stream.empty();
  }
}
