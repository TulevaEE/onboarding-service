package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.ESCALATION_LOOKBACK_DAYS;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.ESCALATION_NET_TD_THRESHOLD;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.ESCALATION_THRESHOLD_DAYS;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_BREACH_THRESHOLD;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_MAX_DAILY_RETURN;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TrackingDifferenceCalculator {

  private static final int SCALE = 6;
  private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");

  private final InvestmentParameterRepository parameterRepository;

  BigDecimal breachThreshold(LocalDate asOf) {
    return parameterRepository.findLatestValue(TRACKING_BREACH_THRESHOLD, asOf);
  }

  int escalationLookbackDays(LocalDate asOf) {
    BigDecimal raw = parameterRepository.findLatestValue(ESCALATION_LOOKBACK_DAYS, asOf);
    int value;
    try {
      value = raw.intValueExact();
    } catch (ArithmeticException e) {
      throw new IllegalStateException("ESCALATION_LOOKBACK_DAYS must be a whole number: " + raw, e);
    }
    if (value < 1) {
      throw new IllegalStateException("ESCALATION_LOOKBACK_DAYS must be positive: " + value);
    }
    return value;
  }

  int escalationThresholdDays(LocalDate asOf) {
    BigDecimal raw = parameterRepository.findLatestValue(ESCALATION_THRESHOLD_DAYS, asOf);
    int value;
    try {
      value = raw.intValueExact();
    } catch (ArithmeticException e) {
      throw new IllegalStateException(
          "ESCALATION_THRESHOLD_DAYS must be a whole number: " + raw, e);
    }
    if (value < 1) {
      throw new IllegalStateException("ESCALATION_THRESHOLD_DAYS must be positive: " + value);
    }
    return value;
  }

  BigDecimal escalationNetTdThreshold(LocalDate asOf) {
    BigDecimal value = parameterRepository.findLatestValue(ESCALATION_NET_TD_THRESHOLD, asOf);
    if (value.signum() <= 0) {
      throw new IllegalStateException("ESCALATION_NET_TD_THRESHOLD must be positive: " + value);
    }
    return value;
  }

  Optional<TrackingDifferenceResult> calculate(TrackingInput input) {
    if (input.yesterdayNav().signum() == 0) {
      return Optional.empty();
    }

    BigDecimal breachThreshold = breachThreshold(input.checkDate());
    BigDecimal maxDailyReturn =
        parameterRepository.findLatestValue(TRACKING_MAX_DAILY_RETURN, input.checkDate());

    var fundReturn =
        input
            .todayNav()
            .subtract(input.yesterdayNav())
            .divide(input.yesterdayNav(), SCALE, HALF_UP);

    var validSecurities =
        input.securities().stream()
            .filter(s -> s.today().price() != null && s.previous().price() != null)
            .filter(s -> s.previous().price().signum() != 0)
            .toList();

    var benchmarkReturn =
        validSecurities.stream()
            .map(
                s -> {
                  var secReturn =
                      safeDailyReturn(s.today().price(), s.previous().price(), maxDailyReturn);
                  return s.modelWeight().multiply(secReturn);
                })
            .reduce(ZERO, BigDecimal::add)
            .setScale(SCALE, HALF_UP);

    var trackingDifference = fundReturn.subtract(benchmarkReturn).setScale(SCALE, HALF_UP);
    var breach = trackingDifference.abs().compareTo(breachThreshold) >= 0;

    var securityAttributions =
        validSecurities.stream()
            .map(
                s -> {
                  var secReturn =
                      safeDailyReturn(s.today().price(), s.previous().price(), maxDailyReturn);
                  var weightDiff =
                      s.actualWeight().subtract(s.modelWeight()).setScale(SCALE, HALF_UP);
                  var contribution = weightDiff.multiply(secReturn).setScale(SCALE, HALF_UP);
                  return new SecurityAttribution(
                      s.isin(),
                      s.modelWeight(),
                      s.actualWeight(),
                      weightDiff,
                      secReturn,
                      null,
                      contribution);
                })
            .toList();

    var cashDrag = input.cashWeight().negate().multiply(benchmarkReturn).setScale(SCALE, HALF_UP);

    var feeDrag =
        input.annualFeeRate().signum() != 0
            ? input.annualFeeRate().negate().divide(DAYS_IN_YEAR, SCALE, HALF_UP)
            : ZERO;

    var attributedSum =
        securityAttributions.stream()
            .map(SecurityAttribution::contribution)
            .reduce(ZERO, BigDecimal::add)
            .add(cashDrag)
            .add(feeDrag);

    var residual = trackingDifference.subtract(attributedSum).setScale(SCALE, HALF_UP);

    // NAV-correctness view: compare the realised fund NAV return against the return implied by the
    // holdings the fund actually held entering the day (begin-of-day / yesterday's EOD snapshot).
    // On a MOC trade day the fund earns its begin-of-day portfolio's return intraday, so this
    // residual collapses to ~0 even though the fund-vs-model TD does not — that is what the NAV
    // gate
    // keys on. A genuine price error on a held (untraded) instrument still surfaces here. When the
    // begin-of-day snapshot is unavailable (null fraction / empty holdings) navResidual is not
    // computable, so we fail soft: leave navResidual null (not evaluated, distinct from an
    // evaluated 0) and do NOT raise navResidualBreach (the gate must not block on data we could
    // not validate, nor manufacture a residual from zero weights).
    BigDecimal impliedFundReturn = null;
    BigDecimal navResidual = null;
    var navResidualBreach = false;
    if (input.bodSecuritiesFraction() != null
        && input.bodHoldings() != null
        && !input.bodHoldings().isEmpty()) {
      var impliedSleeveReturn =
          input.bodHoldings().stream()
              .filter(b -> b.today().price() != null && b.previous().price() != null)
              .filter(b -> b.previous().price().signum() != 0)
              .map(
                  b ->
                      b.weight()
                          .multiply(
                              safeDailyReturn(
                                  b.today().price(), b.previous().price(), maxDailyReturn)))
              .reduce(ZERO, BigDecimal::add);
      impliedFundReturn =
          input
              .bodSecuritiesFraction()
              .multiply(impliedSleeveReturn)
              .add(feeDrag)
              .setScale(SCALE, HALF_UP);
      navResidual = fundReturn.subtract(impliedFundReturn).setScale(SCALE, HALF_UP);
      navResidualBreach = navResidual.abs().compareTo(breachThreshold) >= 0;
    }

    return Optional.of(
        TrackingDifferenceResult.builder()
            .fund(input.fund())
            .checkDate(input.checkDate())
            .checkType(input.checkType())
            .trackingDifference(trackingDifference)
            .fundReturn(fundReturn)
            .benchmarkReturn(benchmarkReturn)
            .breach(breach)
            .consecutiveBreachDays(input.consecutiveBreachDays())
            .securityAttributions(securityAttributions)
            .cashDrag(cashDrag)
            .feeDrag(feeDrag)
            .residual(residual)
            .impliedFundReturn(impliedFundReturn)
            .navResidual(navResidual)
            .navResidualBreach(navResidualBreach)
            .build());
  }

  BigDecimal maxDailyReturn(LocalDate asOf) {
    return parameterRepository.findLatestValue(TRACKING_MAX_DAILY_RETURN, asOf);
  }

  BigDecimal safeDailyReturn(BigDecimal today, BigDecimal yesterday, BigDecimal maxDailyReturn) {
    var ret = today.subtract(yesterday).divide(yesterday, SCALE, HALF_UP);
    return ret.abs().compareTo(maxDailyReturn) > 0 ? ZERO : ret;
  }

  @Builder
  record TrackingInput(
      TulevaFund fund,
      LocalDate checkDate,
      TrackingCheckType checkType,
      BigDecimal todayNav,
      BigDecimal yesterdayNav,
      List<SecurityData> securities,
      BigDecimal cashWeight,
      BigDecimal annualFeeRate,
      int consecutiveBreachDays,
      // Begin-of-day holdings (yesterday's EOD security snapshot) and their share of total NAV,
      // used for the NAV-correctness residual. Null/empty when not supplied (non-MODEL_PORTFOLIO
      // callers and unit fixtures) — navResidual then degenerates to fund return minus fee drag.
      @Nullable List<BodHolding> bodHoldings,
      @Nullable BigDecimal bodSecuritiesFraction) {}

  record PriceSnapshot(@Nullable BigDecimal price, @Nullable LocalDate date) {}

  record SecurityData(
      String isin,
      BigDecimal modelWeight,
      BigDecimal actualWeight,
      PriceSnapshot today,
      PriceSnapshot previous) {}

  // A security the fund held entering the day, weighted within the begin-of-day securities sleeve.
  record BodHolding(String isin, BigDecimal weight, PriceSnapshot today, PriceSnapshot previous) {}
}
