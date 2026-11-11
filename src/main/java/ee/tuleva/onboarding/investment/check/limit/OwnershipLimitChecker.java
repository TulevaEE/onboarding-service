package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.LimitSeverity.SISEKORD_4_P_2_4_3_11;

import ee.tuleva.onboarding.investment.check.limit.LimitSeverity.Reading;
import ee.tuleva.onboarding.investment.check.limit.UnderlyingFunds.SizeInEur;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
class OwnershipLimitChecker {

  OwnershipBreach check(
      String isin,
      String name,
      BigDecimal holdingValue,
      SizeInEur.Known underlyingFundSize,
      OwnershipLimit limit) {
    var actualPercent = shareOfTheUnderlyingFund(holdingValue, underlyingFundSize.amount());
    return new OwnershipBreach(
        isin,
        name,
        holdingValue,
        underlyingFundSize.amount(),
        underlyingFundSize.reportedAmount(),
        underlyingFundSize.reportedCurrency(),
        underlyingFundSize.reportedUpdatedAt(),
        actualPercent,
        limit.softLimitPercent(),
        limit.hardLimitPercent(),
        determineSeverity(actualPercent, limit));
  }

  private BigDecimal shareOfTheUnderlyingFund(BigDecimal holdingValue, BigDecimal fundSize) {
    return holdingValue.multiply(BigDecimal.valueOf(100)).divide(fundSize, 4, RoundingMode.HALF_UP);
  }

  private BreachSeverity determineSeverity(BigDecimal actualPercent, OwnershipLimit limit) {
    return SISEKORD_4_P_2_4_3_11.decide(
        new Reading(actualPercent, limit.softLimitPercent(), limit.hardLimitPercent()));
  }
}
