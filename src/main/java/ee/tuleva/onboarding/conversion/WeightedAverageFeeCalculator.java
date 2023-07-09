package ee.tuleva.onboarding.conversion;

import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.account.FundBalance;
import ee.tuleva.onboarding.mandate.application.Exchange;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WeightedAverageFeeCalculator {

  public BigDecimal getWeightedAverageFee(
      List<FundBalance> fundBalances, List<Exchange> pendingExchanges) {

    Map<String, Asset> assetsByIsin =
        fundBalances.stream()
            .collect(
                toMap(
                    FundBalance::getIsin,
                    fundBalance -> new Asset(fundBalance.getFee(), fundBalance.getTotalValue())));

    for (Exchange exchange : pendingExchanges) {
      String sourceIsin = exchange.getSourceIsin();
      Asset source =
          assetsByIsin.getOrDefault(sourceIsin, new Asset(exchange.getSourceFundFees(), ZERO));
      BigDecimal value = exchange.getValue(source.value);
      Asset subtracted = source.subtract(value);
      assetsByIsin.put(sourceIsin, subtracted);

      if (!exchange.isToPik()) {
        String targetIsin = exchange.getTargetIsin();
        Asset target =
            assetsByIsin.getOrDefault(targetIsin, new Asset(exchange.getTargetFundFees(), ZERO));
        Asset added = target.add(value);
        assetsByIsin.put(targetIsin, added);
      }
    }

    BigDecimal totalValue =
        assetsByIsin.values().stream().map(asset -> asset.value).reduce(ZERO, BigDecimal::add);

    if (totalValue.compareTo(ZERO) == 0) {
      return ZERO;
    }

    return assetsByIsin.values().stream()
        .map(asset -> asset.value.multiply(asset.fee).divide(totalValue, 4, RoundingMode.HALF_UP))
        .reduce(ZERO, BigDecimal::add);
  }

  record Asset(BigDecimal fee, BigDecimal value) {
    public Asset add(BigDecimal augend) {
      return new Asset(fee, value.add(augend));
    }

    public Asset subtract(BigDecimal subtrahend) {
      return new Asset(fee, value.subtract(subtrahend));
    }
  }
}
