package ee.tuleva.onboarding.investment.check.tracking;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class TrackingDifferenceCalculator {

  static final BigDecimal BREACH_THRESHOLD = new BigDecimal("0.0010");
  static final BigDecimal MAX_DAILY_RETURN = new BigDecimal("0.25");
  private static final int SCALE = 6;
  private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");

  Optional<TrackingDifferenceResult> calculate(TrackingInput input) {
    if (input.yesterdayNav().signum() == 0) {
      return Optional.empty();
    }

    var fundReturn =
        input
            .todayNav()
            .subtract(input.yesterdayNav())
            .divide(input.yesterdayNav(), SCALE, HALF_UP);

    var validSecurities =
        input.securities().stream()
            .filter(s -> s.todayPrice() != null && s.yesterdayPrice() != null)
            .filter(s -> s.yesterdayPrice().signum() != 0)
            .toList();

    var benchmarkReturn =
        validSecurities.stream()
            .map(
                s -> {
                  var secReturn = safeDailyReturn(s.todayPrice(), s.yesterdayPrice());
                  return s.modelWeight().multiply(secReturn);
                })
            .reduce(ZERO, BigDecimal::add)
            .setScale(SCALE, HALF_UP);

    var trackingDifference = fundReturn.subtract(benchmarkReturn).setScale(SCALE, HALF_UP);
    var breach = trackingDifference.abs().compareTo(BREACH_THRESHOLD) >= 0;

    var securityAttributions =
        validSecurities.stream()
            .map(
                s -> {
                  var secReturn = safeDailyReturn(s.todayPrice(), s.yesterdayPrice());
                  var weightDiff =
                      s.actualWeight().subtract(s.modelWeight()).setScale(SCALE, HALF_UP);
                  var contribution = weightDiff.multiply(secReturn).setScale(SCALE, HALF_UP);
                  return new SecurityAttribution(
                      s.isin(),
                      s.modelWeight(),
                      s.actualWeight(),
                      weightDiff,
                      secReturn,
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
            .build());
  }

  private BigDecimal safeDailyReturn(BigDecimal today, BigDecimal yesterday) {
    var ret = today.subtract(yesterday).divide(yesterday, SCALE, HALF_UP);
    return ret.abs().compareTo(MAX_DAILY_RETURN) > 0 ? ZERO : ret;
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
      int consecutiveBreachDays) {}

  record SecurityData(
      String isin,
      BigDecimal modelWeight,
      BigDecimal actualWeight,
      @Nullable BigDecimal todayPrice,
      @Nullable BigDecimal yesterdayPrice) {}
}
