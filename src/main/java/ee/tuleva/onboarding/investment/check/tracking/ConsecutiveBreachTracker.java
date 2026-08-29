package ee.tuleva.onboarding.investment.check.tracking;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.fund.TulevaFund;
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

  private final TrackingDifferenceEventRepository eventRepository;
  private final TrackingDifferenceCalculator calculator;

  record ConsecutiveBreachInfo(
      int count,
      BigDecimal compoundedTd,
      BigDecimal compoundedFundReturn,
      BigDecimal compoundedBenchmarkReturn,
      Map<String, BigDecimal> contributionByIsin,
      BigDecimal cashDragSum,
      BigDecimal feeDragSum,
      BigDecimal residualSum,
      boolean hadNavResidualBreach) {}

  ConsecutiveBreachInfo countConsecutiveBreaches(
      TulevaFund fund, TrackingCheckType checkType, LocalDate checkDate) {
    try {
      return doCountConsecutiveBreaches(fund, checkType, checkDate);
    } catch (Exception e) {
      log.warn(
          "Escalation count failed, using empty: fund={}, checkType={}, error={}",
          fund,
          checkType,
          e.getMessage());
      return new ConsecutiveBreachInfo(0, ZERO, ZERO, ZERO, Map.of(), ZERO, ZERO, ZERO, false);
    }
  }

  private ConsecutiveBreachInfo doCountConsecutiveBreaches(
      TulevaFund fund, TrackingCheckType checkType, LocalDate checkDate) {
    int lookback;
    try {
      lookback = calculator.escalationLookbackDays(checkDate);
    } catch (IllegalStateException e) {
      log.warn("Escalation parameters not configured, using fallback: {}", e.getMessage());
      lookback = ESCALATION_LOOKBACK_FALLBACK;
    } catch (Exception e) {
      log.warn("Escalation lookback parameter lookup failed, using fallback: {}", e.getMessage());
      lookback = ESCALATION_LOOKBACK_FALLBACK;
    }
    var recent = eventRepository.findMostRecentEvents(fund, checkType, checkDate, lookback);
    int count = 0;
    var compoundedFund = BigDecimal.ONE;
    var compoundedBenchmark = BigDecimal.ONE;
    var cashDragSum = ZERO;
    var feeDragSum = ZERO;
    var residualSum = ZERO;
    var hadNavResidualBreach = false;
    var contributionByIsin = new LinkedHashMap<String, BigDecimal>();

    for (var event : recent) {
      var navResidualBreach = Boolean.TRUE.equals(event.getResult().get("navResidualBreach"));
      if (!event.isBreach() && !navResidualBreach) {
        break;
      }
      count++;
      hadNavResidualBreach = hadNavResidualBreach || navResidualBreach;
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

    return new ConsecutiveBreachInfo(
        count,
        compoundedTd,
        compoundedFundReturn,
        compoundedBenchmarkReturn,
        contributionByIsin,
        cashDragSum,
        feeDragSum,
        residualSum,
        hadNavResidualBreach);
  }

  TrackingDifferenceResult updateConsecutiveCount(
      TrackingDifferenceResult result, ConsecutiveBreachInfo priorBreaches) {
    if (!result.breach() && !result.navResidualBreach()) {
      return result.toBuilder().consecutiveBreachDays(0).consecutiveNetTd(ZERO).build();
    }
    int days = priorBreaches.count() + 1;
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
        .consecutiveNetTd(compoundedTd)
        .escalationNavResidualBreach(streakHadNavResidualBreach)
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
