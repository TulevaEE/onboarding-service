package ee.tuleva.onboarding.conversion;

import ee.tuleva.onboarding.account.FundBalance;
import ee.tuleva.onboarding.mandate.application.Exchange;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WeightedAverageFeeCalculator {

  public BigDecimal getWeightedAverageFee(
      List<FundBalance> funds, List<Exchange> pendingExchanges) {
    pendingExchanges.forEach(
        e ->
            log.info(
                "Pending exchangesFrom {}, To: {}, amount: {}, pik: {}",
                e.getSourceFund(),
                e.getTargetFund(),
                e.getAmount(),
                e.getTargetPik()));

    if (funds.size() == 0) {
      return BigDecimal.ZERO;
    }

    BigDecimal valueSum = getValueSum(funds);
    if (valueSum.compareTo(BigDecimal.ZERO) == 0) {
      var fromFundIsins =
          pendingExchanges.stream().map(exchange -> exchange.getSourceFund().getIsin()).toList();
      var fundsThatDoNotIncludeLeavingFunds =
          funds.stream()
              .filter(fundBalance -> !fromFundIsins.contains(fundBalance.getFund().getIsin()))
              .toList();

      var weightedValue =
          fundsThatDoNotIncludeLeavingFunds.stream()
              .map(fund -> fund.getFund().getOngoingChargesFigure())
              .reduce(BigDecimal.ZERO, BigDecimal::add)
              .divide(BigDecimal.valueOf(funds.size()), RoundingMode.HALF_UP);

      var toFundWeightedValue =
          pendingExchanges.stream()
              .map(this::getOngoingChargesFigure)
              .reduce(BigDecimal.ZERO, BigDecimal::add)
              .divide(BigDecimal.valueOf(funds.size()), RoundingMode.HALF_UP);

      return weightedValue.add(toFundWeightedValue);
    }

    BigDecimal weightedArithmeticMean = BigDecimal.ZERO;
    for (FundBalance fund : funds) {
      BigDecimal fundTotalValue = fund.getTotalValue();
      var exchangesFromCurrentFund =
          pendingExchanges.stream()
              .filter(exchange -> exchange.getSourceFund().getIsin().equals(fund.getIsin()))
              .toList();

      var amountThatLeavesThisFund =
          exchangesFromCurrentFund.stream()
              .map(Exchange::getAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      var weightedValueOfExchanges =
          exchangesFromCurrentFund.stream()
              .map(
                  exchange ->
                      exchange
                          .getAmount()
                          .multiply(getOngoingChargesFigure(exchange))
                          .divide(valueSum, 4, RoundingMode.HALF_UP))
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      log.info(
          "Fund {}. {} exchanges that leave this fund. Amount that leaves is {}. Weighted value of the exchanges is {}",
          fund.getIsin(),
          exchangesFromCurrentFund.size(),
          amountThatLeavesThisFund,
          weightedValueOfExchanges);

      exchangesFromCurrentFund.forEach(
          e ->
              log.info(
                  "Pending exchanges from current fund: From {}, To: {}, amount: {}, pik: {}",
                  e.getSourceFund(),
                  e.getTargetFund(),
                  e.getAmount(),
                  e.getTargetPik()));

      BigDecimal weightedValue =
          (fundTotalValue.subtract(amountThatLeavesThisFund))
              .multiply(fund.getFund().getOngoingChargesFigure())
              .divide(valueSum, 4, RoundingMode.HALF_UP);
      weightedArithmeticMean =
          weightedArithmeticMean.add(weightedValue).add(weightedValueOfExchanges);
    }
    return weightedArithmeticMean;
  }

  private BigDecimal getValueSum(List<FundBalance> funds) {
    return funds.stream().map(FundBalance::getTotalValue).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal getOngoingChargesFigure(Exchange exchange) {
    return (exchange.getTargetFund() == null)
        ? BigDecimal.ZERO
        : exchange.getTargetFund().getOngoingChargesFigure();
  }
}
