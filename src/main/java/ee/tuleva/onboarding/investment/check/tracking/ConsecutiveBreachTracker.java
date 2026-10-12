package ee.tuleva.onboarding.investment.check.tracking;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.TrackingCheckType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class ConsecutiveBreachTracker {

  private static final int ESCALATION_LOOKBACK_FALLBACK = 10;
  private static final int LONGEST_UNCHECKED_GAP_A_STREAK_BRIDGES = 10;

  private final TrackingDifferenceEventRepository eventRepository;
  private final TrackingDifferenceCalculator calculator;
  private final PublicHolidays publicHolidays;

  record ConsecutiveBreachInfo(
      int count,
      BigDecimal compoundedTd,
      BigDecimal compoundedFundReturn,
      BigDecimal compoundedBenchmarkReturn,
      Map<String, BigDecimal> contributionByIsin,
      BigDecimal cashDragSum,
      BigDecimal feeDragSum,
      BigDecimal residualSum,
      boolean hadNavResidualBreach,
      boolean truncated,
      boolean unavailable,
      int uncheckedDays,
      int uncheckedDaysSince) {

    int streakDaysBeforeTheCheckDate() {
      return count + uncheckedDaysSince;
    }

    int uncheckedStreakDaysBeforeTheCheckDate() {
      return uncheckedDays + uncheckedDaysSince;
    }

    int streakDaysIfTheCheckDateBreaches() {
      return streakDaysBeforeTheCheckDate() + 1;
    }

    boolean owesTheNotification(int notificationWorkingDay, BigDecimal netTdThreshold) {
      return count < notificationWorkingDay
          && streakDaysBeforeTheCheckDate() >= notificationWorkingDay - 1
          && (hadNavResidualBreach || compoundedTd.abs().compareTo(netTdThreshold) >= 0);
    }
  }

  ConsecutiveBreachInfo countConsecutiveBreaches(
      TulevaFund fund, TrackingCheckType checkType, LocalDate checkDate) {
    try {
      return doCountConsecutiveBreaches(fund, checkType, checkDate);
    } catch (Exception e) {
      log.error(
          "Escalation count failed: fund={}, checkType={}, checkDate={}, error={}",
          fund,
          checkType,
          checkDate,
          e.getMessage());
      return new ConsecutiveBreachInfo(
          0, ZERO, ZERO, ZERO, Map.of(), ZERO, ZERO, ZERO, false, false, true, 0, 0);
    }
  }

  private ConsecutiveBreachInfo doCountConsecutiveBreaches(
      TulevaFund fund, TrackingCheckType checkType, LocalDate checkDate) {
    int lookback = escalationLookbackDays(checkDate);
    var recent = eventRepository.findMostRecentEvents(fund, checkType, checkDate, lookback);
    int count = 0;
    int checkedDays = 0;
    int uncheckedDays = 0;
    int uncheckedDaysSince = 0;
    var compoundedFund = BigDecimal.ONE;
    var compoundedBenchmark = BigDecimal.ONE;
    var cashDragSum = ZERO;
    var feeDragSum = ZERO;
    var residualSum = ZERO;
    var hadNavResidualBreach = false;
    var contributionByIsin = new LinkedHashMap<String, BigDecimal>();

    var laterDate = checkDate;
    for (var event : recent) {
      var uncheckedBefore = uncheckedWorkingDaysBetween(event.getCheckDate(), laterDate);
      laterDate = event.getCheckDate();

      if (!isBreachDay(event)) {
        break;
      }
      if (uncheckedBefore > LONGEST_UNCHECKED_GAP_A_STREAK_BRIDGES) {
        warnOfAGapTooLongToBridge(fund, checkType, checkDate, event, uncheckedBefore);
        break;
      }
      warnOfUncheckedDays(fund, checkType, checkDate, event, uncheckedBefore);
      if (checkedDays == 0) {
        uncheckedDaysSince = uncheckedBefore;
      } else {
        count += uncheckedBefore;
        uncheckedDays += uncheckedBefore;
      }
      count++;
      checkedDays++;
      hadNavResidualBreach = hadNavResidualBreach || hadNavResidualBreach(event);
      compoundedFund = compoundedFund.multiply(BigDecimal.ONE.add(event.getFundReturn()));
      compoundedBenchmark =
          compoundedBenchmark.multiply(BigDecimal.ONE.add(event.getBenchmarkReturn()));

      try {
        var payload = TrackingDifferenceEventMapper.parseEventPayload(event.getResult());
        cashDragSum = cashDragSum.add(payload.cashDrag());
        feeDragSum = feeDragSum.add(payload.feeDrag());
        residualSum = residualSum.add(payload.residual());
        payload
            .contributionByIsin()
            .forEach(
                (isin, contribution) ->
                    contributionByIsin.merge(isin, contribution, BigDecimal::add));
      } catch (Exception e) {
        log.warn(
            "Failed to parse attribution from event: checkDate={}, error={}",
            event.getCheckDate(),
            e.getMessage());
      }
    }

    var compoundedFundReturn = compoundedFund.subtract(BigDecimal.ONE);
    var compoundedBenchmarkReturn = compoundedBenchmark.subtract(BigDecimal.ONE);
    var compoundedTd = compoundedFundReturn.subtract(compoundedBenchmarkReturn);

    var streakMayRunPastTheLookbackWindow =
        checkedDays > 0 && checkedDays == recent.size() && recent.size() >= lookback;
    if (streakMayRunPastTheLookbackWindow) {
      log.warn(
          "Escalation streak fills the whole lookback window: fund={}, checkType={}, checkDate={}, lookbackDays={}",
          fund,
          checkType,
          checkDate,
          lookback);
    }

    return new ConsecutiveBreachInfo(
        count,
        compoundedTd,
        compoundedFundReturn,
        compoundedBenchmarkReturn,
        contributionByIsin,
        cashDragSum,
        feeDragSum,
        residualSum,
        hadNavResidualBreach,
        streakMayRunPastTheLookbackWindow,
        false,
        uncheckedDays,
        uncheckedDaysSince);
  }

  private int escalationLookbackDays(LocalDate checkDate) {
    try {
      return calculator.escalationLookbackDays(checkDate);
    } catch (IllegalStateException e) {
      log.warn("Escalation parameters not configured, using fallback: {}", e.getMessage());
      return ESCALATION_LOOKBACK_FALLBACK;
    } catch (Exception e) {
      log.warn("Escalation lookback parameter lookup failed, using fallback: {}", e.getMessage());
      return ESCALATION_LOOKBACK_FALLBACK;
    }
  }

  private static boolean isBreachDay(TrackingDifferenceEvent event) {
    return event.isBreach() || hadNavResidualBreach(event);
  }

  private static boolean hadNavResidualBreach(TrackingDifferenceEvent event) {
    return Boolean.TRUE.equals(event.getResult().get("navResidualBreach"));
  }

  private static void warnOfUncheckedDays(
      TulevaFund fund,
      TrackingCheckType checkType,
      LocalDate checkDate,
      TrackingDifferenceEvent event,
      int uncheckedDays) {
    if (uncheckedDays > 0) {
      log.warn(
          "Escalation streak runs across working days with no check: fund={}, checkType={}, checkDate={}, uncheckedDays={}, after={}",
          fund,
          checkType,
          checkDate,
          uncheckedDays,
          event.getCheckDate());
    }
  }

  private static void warnOfAGapTooLongToBridge(
      TulevaFund fund,
      TrackingCheckType checkType,
      LocalDate checkDate,
      TrackingDifferenceEvent event,
      int uncheckedDays) {
    log.warn(
        "Escalation streak ends at working days with no check, too many to bridge: fund={}, checkType={}, checkDate={}, uncheckedDays={}, longestBridged={}, after={}",
        fund,
        checkType,
        checkDate,
        uncheckedDays,
        LONGEST_UNCHECKED_GAP_A_STREAK_BRIDGES,
        event.getCheckDate());
  }

  private int uncheckedWorkingDaysBetween(LocalDate earlier, LocalDate later) {
    int days = 0;
    for (var day = publicHolidays.previousWorkingDay(later);
        day.isAfter(earlier);
        day = publicHolidays.previousWorkingDay(day)) {
      days++;
    }
    return days;
  }

  TrackingDifferenceResult updateConsecutiveCount(
      TrackingDifferenceResult result, ConsecutiveBreachInfo priorBreaches) {
    if (!result.breach() && !result.navResidualBreach()) {
      return result.toBuilder()
          .consecutiveBreachDays(0)
          .consecutiveNetTd(ZERO)
          .escalationCountUnavailable(priorBreaches.unavailable())
          .streakBefore(priorBreaches)
          .build();
    }
    int days = priorBreaches.streakDaysIfTheCheckDateBreaches();
    var streakHadNavResidualBreach =
        priorBreaches.hadNavResidualBreach() || result.navResidualBreach();

    var compoundedFund =
        BigDecimal.ONE
            .add(priorBreaches.compoundedFundReturn())
            .multiply(BigDecimal.ONE.add(result.fundReturn()))
            .subtract(BigDecimal.ONE);
    var compoundedBenchmark =
        BigDecimal.ONE
            .add(priorBreaches.compoundedBenchmarkReturn())
            .multiply(BigDecimal.ONE.add(result.benchmarkReturn()))
            .subtract(BigDecimal.ONE);
    var compoundedTd = compoundedFund.subtract(compoundedBenchmark);

    return result.toBuilder()
        .consecutiveBreachDays(days)
        .streakBefore(priorBreaches)
        .escalationUncheckedDays(priorBreaches.uncheckedStreakDaysBeforeTheCheckDate())
        .consecutiveNetTd(compoundedTd)
        .escalationNavResidualBreach(streakHadNavResidualBreach)
        .escalationCountTruncated(priorBreaches.truncated())
        .escalationCountUnavailable(priorBreaches.unavailable())
        .compoundedFundReturn(compoundedFund)
        .compoundedBenchmarkReturn(compoundedBenchmark)
        .escalationAttributions(
            TrackingDifferenceEventMapper.mergeAttributions(
                priorBreaches.contributionByIsin(), result.securityAttributions()))
        .escalationCashDrag(
            priorBreaches.cashDragSum().add(result.cashDrag() != null ? result.cashDrag() : ZERO))
        .escalationFeeDrag(
            priorBreaches.feeDragSum().add(result.feeDrag() != null ? result.feeDrag() : ZERO))
        .escalationResidual(
            priorBreaches.residualSum().add(result.residual() != null ? result.residual() : ZERO))
        .build();
  }
}
