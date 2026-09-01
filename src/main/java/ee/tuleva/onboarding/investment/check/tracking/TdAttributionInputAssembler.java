package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.check.tracking.TdAttributionCalculator.DailyRecord;
import ee.tuleva.onboarding.investment.check.tracking.TdAttributionCalculator.SecurityDailyData;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    // Every weight comes from the daily check that actually ran, never from today's allocation
    // table: a retroactive correction there must not rewrite a month that has already been
    // measured, and the transition blending belongs in one place - the daily check - not two.
    var securityDataList = new ArrayList<SecurityDailyData>();
    var unreproducible = new ArrayList<String>();

    for (var attr : attributions) {
      var isin = Objects.requireNonNull((String) attr.get("isin"), "Attribution missing isin");

      // Events written before the daily check stored modelWeight cannot be reproduced. Leaving
      // the instrument out keeps its effect in the residual; reading a missing weight as zero
      // would report a model that never held it.
      if (!attr.containsKey("modelWeight") || !attr.containsKey("weightDifference")) {
        unreproducible.add(isin);
        continue;
      }

      var position = positionByIsin.get(isin);
      securityDataList.add(
          SecurityDailyData.builder()
              .isin(isin)
              .instrumentName(position != null ? position.getAccountName() : null)
              .modelWeight(toBigDecimal(attr.get("modelWeight")))
              .actualWeight(toBigDecimal(attr.get("actualWeight")))
              .normalizedWeightDiff(toBigDecimal(attr.get("weightDifference")))
              .securityReturn(toBigDecimal(attr.get("securityReturn")))
              .build());
    }

    if (!unreproducible.isEmpty()) {
      log.warn(
          "Daily attribution predates the stored model weight, leaving those instruments out of the period detail: fund={}, date={}, isins={}",
          fund,
          date,
          unreproducible);
    }

    return securityDataList;
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
