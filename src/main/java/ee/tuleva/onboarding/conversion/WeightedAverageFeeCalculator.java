package ee.tuleva.onboarding.conversion;

import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.account.FundBalance;
import ee.tuleva.onboarding.mandate.application.Exchange;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WeightedAverageFeeCalculator {

  public BigDecimal getWeightedAverageFee(
      List<FundBalance> fundBalances, List<Exchange> pendingExchanges) {

    Map<String, Asset> balanceAssetsByIsin =
        fundBalances.stream()
            .collect(
                toMap(
                    FundBalance::getIsin,
                    fundBalance -> new Asset(fundBalance.getFee(), fundBalance.getTotalValue())));

    log.info("balanceAssetsByIsin: {}", balanceAssetsByIsin);

    Map<String, Asset> exchangeAssetsByIsin = new HashMap<>();
    for (Exchange exchange : pendingExchanges) {
      Asset asset =
          balanceAssetsByIsin.getOrDefault(
              exchange.getSourceIsin(), new Asset(exchange.getSourceFundFees(), ZERO));
      BigDecimal value = exchange.getValue(asset.value);

      String sourceIsin = exchange.getSourceIsin();
      Asset source =
          exchangeAssetsByIsin.getOrDefault(
              sourceIsin, new Asset(exchange.getSourceFundFees(), ZERO));
      Asset subtracted = source.subtract(value);
      exchangeAssetsByIsin.put(sourceIsin, subtracted);

      if (!exchange.isToPik()) {
        String targetIsin = exchange.getTargetIsin();
        Asset target =
            exchangeAssetsByIsin.getOrDefault(
                targetIsin, new Asset(exchange.getTargetFundFees(), ZERO));
        Asset added = target.add(value);
        exchangeAssetsByIsin.put(targetIsin, added);
      }
    }

    log.info("exchangeAssetsByIsin: {}", exchangeAssetsByIsin);

    var mergedAssetsByIsin = new HashMap<>(balanceAssetsByIsin);
    exchangeAssetsByIsin.forEach(
        (isin, asset) -> mergedAssetsByIsin.merge(isin, asset, Asset::add));

    log.info("mergedAssetsByIsin: {}", mergedAssetsByIsin);

    BigDecimal totalValue =
        mergedAssetsByIsin.values().stream()
            .map(asset -> asset.value)
            .reduce(ZERO, BigDecimal::add);

    log.info("totalValue: {}", totalValue);

    if (totalValue.compareTo(ZERO) == 0) {
      return ZERO;
    }

    return mergedAssetsByIsin.values().stream()
        .map(asset -> asset.value.multiply(asset.fee).divide(totalValue, 4, RoundingMode.HALF_UP))
        .reduce(ZERO, BigDecimal::add);
  }

  record Asset(BigDecimal fee, BigDecimal value) {
    public Asset add(BigDecimal augend) {
      return new Asset(fee, value.add(augend));
    }

    public Asset add(Asset augend) {
      if (fee.compareTo(augend.fee) != 0) {
        throw new IllegalArgumentException("Different fees: " + this + ", " + augend);
      }
      return new Asset(fee, value.add(augend.value));
    }

    public Asset subtract(BigDecimal subtrahend) {
      return new Asset(fee, value.subtract(subtrahend));
    }
  }
}
