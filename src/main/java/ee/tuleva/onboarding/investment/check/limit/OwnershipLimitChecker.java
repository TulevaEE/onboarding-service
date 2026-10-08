package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.HARD;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.SOFT;

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
    if (actualPercent.compareTo(limit.hardLimitPercent()) >= 0) {
      return HARD;
    }
    if (actualPercent.compareTo(limit.softLimitPercent()) > 0) {
      return SOFT;
    }
    return OK;
  }
}
