package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.check.tracking.TdAttributionCalculator.DailyRecord;
import ee.tuleva.onboarding.investment.check.tracking.TdAttributionCalculator.SecurityDailyData;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class TdAttributionInputAssembler {

  private static final int SCALE = TdAttributionCalculator.SCALE;

  private final FundPositionRepository fundPositionRepository;
  private final FundNavQueryService fundNavQueryService;

  List<DailyRecord> buildDailyRecords(
      TulevaFund fund,
      List<TrackingDifferenceEvent> tdEvents,
      List<ModelPortfolioAllocation> modelAllocations) {

    var records = new ArrayList<DailyRecord>();

    for (var event : tdEvents) {
      var date = event.getCheckDate();

      var navComponents = loadNavComponents(fund, date);
      if (navComponents == null) {
        log.warn("Missing nav_report data for daily record: fund={}, date={}", fund, date);
        records.add(
            DailyRecord.builder()
                .date(date)
                .fundReturn(event.getFundReturn())
                .modelReturn(event.getBenchmarkReturn())
                .aum(ZERO)
                .cashValue(ZERO)
                .nonSecurityValue(ZERO)
                .securities(List.of())
                .build());
        continue;
      }

      var securityDailyData =
          buildSecurityDailyData(fund, event, modelAllocations, date, navComponents);

      records.add(
          DailyRecord.builder()
              .date(date)
              .fundReturn(event.getFundReturn())
              .modelReturn(event.getBenchmarkReturn())
              .aum(navComponents.aum())
              .cashValue(navComponents.cash())
              .nonSecurityValue(navComponents.nonSecurityValue())
              .securities(securityDailyData)
              .build());
    }

    return records;
  }

  private @Nullable NavComponents loadNavComponents(TulevaFund fund, LocalDate date) {
    var aum = fundNavQueryService.findAum(fund.getCode(), date);
    if (aum == null || aum.signum() <= 0) {
      return null;
    }
    var securities = fundNavQueryService.findSecuritiesTotalValue(fund.getCode(), date);
    var cash = fundNavQueryService.findCashValue(fund.getCode(), date);
    var negativeFeeAccrualLiabilities =
        fundNavQueryService.findFeeAccrualLiabilities(fund.getCode(), date);

    var nonSecurityValue =
        aum.subtract(securities).subtract(cash).subtract(negativeFeeAccrualLiabilities);

    return new NavComponents(aum, securities, cash, nonSecurityValue);
  }

  @SuppressWarnings("unchecked")
  private List<SecurityDailyData> buildSecurityDailyData(
      TulevaFund fund,
      TrackingDifferenceEvent event,
      List<ModelPortfolioAllocation> modelAllocations,
      LocalDate date,
      NavComponents navComponents) {

    var result = event.getResult();
    var attributions =
        (List<Map<String, Object>>) result.getOrDefault("securityAttributions", List.of());
    if (attributions.isEmpty()) {
      return List.of();
    }

    var totalSecurityValue = navComponents.securities();
    if (totalSecurityValue.signum() <= 0) {
      return List.of();
    }

    var positions = fundPositionRepository.findByNavDateAndFundAndAccountType(date, fund, SECURITY);
    var positionByIsin =
        positions.stream()
            .filter(p -> p.getAccountId() != null)
            .collect(Collectors.toMap(FundPosition::getAccountId, p -> p, (a, b) -> a));

    var currentWeights = getModelWeightsForDate(modelAllocations, date);
    var previousWeights = getPreviousModelWeightsForDate(modelAllocations, date);
    var transitionIsins =
        findTransitionIsins(currentWeights, previousWeights, positionByIsin.keySet(), fund);

    var securityDataList = new ArrayList<SecurityDailyData>();

    for (var attr : attributions) {
      var isin = Objects.requireNonNull((String) attr.get("isin"), "Attribution missing isin");
      var securityReturn = toBigDecimal(attr.get("securityReturn"));

      var position = positionByIsin.get(isin);
      var positionMv = position != null ? position.getMarketValue() : null;
      var actualMv = positionMv != null ? positionMv : ZERO;
      var normalizedActualWeight = actualMv.divide(totalSecurityValue, SCALE, HALF_UP);

      var modelWeight =
          transitionIsins.contains(isin)
              ? normalizedActualWeight
              : currentWeights.getOrDefault(isin, ZERO);
      var normalizedWeightDiff = normalizedActualWeight.subtract(modelWeight);

      securityDataList.add(
          SecurityDailyData.builder()
              .isin(isin)
              .instrumentName(position != null ? position.getAccountName() : null)
              .modelWeight(modelWeight)
              .actualWeight(normalizedActualWeight)
              .normalizedWeightDiff(normalizedWeightDiff)
              .securityReturn(securityReturn)
              .build());
    }

    return securityDataList;
  }

  private Map<String, BigDecimal> getModelWeightsForDate(
      List<ModelPortfolioAllocation> allVersions, LocalDate date) {
    var activeAllocations =
        allVersions.stream().filter(a -> !a.getEffectiveDate().isAfter(date)).toList();
    if (activeAllocations.isEmpty()) {
      return Map.of();
    }
    var latestDate =
        activeAllocations.stream()
            .map(ModelPortfolioAllocation::getEffectiveDate)
            .max(LocalDate::compareTo)
            .orElseThrow();
    return activeAllocations.stream()
        .filter(a -> a.getEffectiveDate().equals(latestDate))
        .collect(
            Collectors.toMap(
                ModelPortfolioAllocation::getIsin, ModelPortfolioAllocation::getWeight));
  }

  private Map<String, BigDecimal> getPreviousModelWeightsForDate(
      List<ModelPortfolioAllocation> allVersions, LocalDate date) {
    var activeAllocations =
        allVersions.stream().filter(a -> !a.getEffectiveDate().isAfter(date)).toList();
    var distinctDates =
        activeAllocations.stream()
            .map(ModelPortfolioAllocation::getEffectiveDate)
            .distinct()
            .sorted()
            .toList();
    if (distinctDates.size() < 2) {
      return Map.of();
    }
    var previousDate = distinctDates.get(distinctDates.size() - 2);
    return activeAllocations.stream()
        .filter(a -> a.getEffectiveDate().equals(previousDate))
        .collect(
            Collectors.toMap(
                ModelPortfolioAllocation::getIsin, ModelPortfolioAllocation::getWeight));
  }

  private Set<String> findTransitionIsins(
      Map<String, BigDecimal> currentWeights,
      Map<String, BigDecimal> previousWeights,
      Set<String> positionIsins,
      TulevaFund fund) {

    if (previousWeights.isEmpty()) {
      return Set.of();
    }

    var addedIsins = new HashSet<>(currentWeights.keySet());
    addedIsins.removeAll(previousWeights.keySet());
    var removedIsins = new HashSet<>(previousWeights.keySet());
    removedIsins.removeAll(currentWeights.keySet());

    if (addedIsins.isEmpty() && removedIsins.isEmpty()) {
      return Set.of();
    }

    var knownIsins = new HashSet<>(currentWeights.keySet());
    knownIsins.addAll(previousWeights.keySet());
    var unexpectedIsins = new HashSet<>(positionIsins);
    unexpectedIsins.removeAll(knownIsins);

    if (!unexpectedIsins.isEmpty()) {
      log.warn(
          "Unexpected ISINs in portfolio, skipping transition blending: fund={}, unexpected={}",
          fund,
          unexpectedIsins);
      return Set.of();
    }

    var removedAndHeld = new HashSet<>(removedIsins);
    removedAndHeld.retainAll(positionIsins);

    if (removedAndHeld.isEmpty()) {
      return Set.of();
    }

    var transitionIsins = new HashSet<>(addedIsins);
    transitionIsins.addAll(removedIsins);
    return transitionIsins;
  }

  static int countSeriesGaps(List<LocalDate> sortedEventDates, PublicHolidays publicHolidays) {
    int gaps = 0;
    for (int i = 1; i < sortedEventDates.size(); i++) {
      var previousEventDate = sortedEventDates.get(i - 1);
      var currentEventDate = sortedEventDates.get(i);
      if (!publicHolidays.previousWorkingDay(currentEventDate).equals(previousEventDate)) {
        gaps++;
      }
    }
    return gaps;
  }

  static BigDecimal toBigDecimal(@Nullable Object value) {
    if (value instanceof BigDecimal bd) return bd;
    if (value instanceof Number n) return new BigDecimal(n.toString());
    if (value instanceof String s) return new BigDecimal(s);
    return ZERO;
  }
}
