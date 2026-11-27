package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.investment.transaction.TradePrepPrice.IndexPrice;
import ee.tuleva.onboarding.investment.transaction.TradePrepPrice.ManualPrice;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

@NullMarked
record ManualPrices(List<PriceOverride> overrides) {

  static ManualPrices of(TulevaFund fund, List<String> instruments, List<PriceOverride> overrides) {
    List<String> unknown =
        overrides.stream()
            .map(PriceOverride::isin)
            .filter(isin -> !instruments.contains(isin))
            .sorted()
            .toList();
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException(
          "Manual price for an instrument neither held nor in the model portfolio: fund=%s, isins=%s"
              .formatted(fund.name(), unknown));
    }
    Set<String> seen = new HashSet<>();
    overrides.forEach(
        override -> {
          if (!seen.add(override.isin())) {
            throw new IllegalArgumentException(
                "Two manual prices for one instrument: fund=%s, isin=%s"
                    .formatted(fund.name(), override.isin()));
          }
        });
    return new ManualPrices(List.copyOf(overrides));
  }

  boolean prices(String isin) {
    return overrides.stream().anyMatch(override -> override.isin().equals(isin));
  }

  Map<String, TradePrepPrice> withIndexPrices(Map<String, ResolvedPrice> indexPrices) {
    Map<String, TradePrepPrice> prices = new HashMap<>();
    indexPrices.forEach((isin, price) -> prices.put(isin, new IndexPrice(price)));
    overrides.forEach(override -> prices.put(override.isin(), new ManualPrice(override)));
    return Map.copyOf(prices);
  }

  List<UsedPriceOverride> uses(PriceDeviations deviations, Map<String, ResolvedPrice> indexPrices) {
    return overrides.stream()
        .map(
            override -> {
              ResolvedPrice indexPrice = indexPrices.get(override.isin());
              ManualPriceDeviationCheck check =
                  Objects.requireNonNull(
                      deviations.checks().get(override.isin()),
                      () -> "No deviation check for a manual price: isin=" + override.isin());
              boolean clearedAStop =
                  indexPrice == null && check != ManualPriceDeviationCheck.SKIPPED_EXCLUDED;
              return new UsedPriceOverride(
                  override,
                  clearedAStop ? GateNeed.CLEARED_A_STOP : GateNeed.NOT_NEEDED,
                  check,
                  indexPrice);
            })
        .toList();
  }
}
