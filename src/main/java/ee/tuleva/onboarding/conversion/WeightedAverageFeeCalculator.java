package ee.tuleva.onboarding.conversion;

import ee.tuleva.onboarding.account.FundBalance;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class WeightedAverageFeeCalculator {

  public BigDecimal getValueSum(List<FundBalance> funds) {
    return funds.stream().map(FundBalance::getTotalValue).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public BigDecimal getWeightedAverageFee(List<FundBalance> funds) {

    if (funds.size() == 0) {
      return BigDecimal.ZERO;
    }

    BigDecimal valueSum = getValueSum(funds);
    if (valueSum.equals(BigDecimal.ZERO)) {
      return funds.stream()
          .map(fund -> fund.getFund().getOngoingChargesFigure())
          .reduce(BigDecimal.ZERO, BigDecimal::add)
          .divide(BigDecimal.valueOf(funds.size()), RoundingMode.HALF_UP);
    }

    BigDecimal weightedArithmeticMean = BigDecimal.ZERO;
    for (FundBalance fund : funds) {
      BigDecimal fundTotalValue = fund.getTotalValue();
      BigDecimal weightedValue =
          fundTotalValue
              .multiply(fund.getFund().getOngoingChargesFigure())
              .divide(valueSum, 4, RoundingMode.HALF_UP);
      weightedArithmeticMean = weightedArithmeticMean.add(weightedValue);
    }
    return weightedArithmeticMean;
  }
}
