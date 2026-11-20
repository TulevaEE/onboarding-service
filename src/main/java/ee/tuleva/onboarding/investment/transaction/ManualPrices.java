package ee.tuleva.onboarding.investment.transaction;

import static java.util.stream.Collectors.toUnmodifiableMap;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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

  TradePrepPricing pricing(@Nullable LocalDate priceDate) {
    Map<String, BigDecimal> byIsin =
        overrides.stream().collect(toUnmodifiableMap(PriceOverride::isin, PriceOverride::price));
    return new TradePrepPricing(byIsin, priceDate);
  }

  List<UsedPriceOverride> uses(Set<String> excludedIsins, Map<String, ResolvedPrice> indexPrices) {
    return overrides.stream()
        .map(
            override -> {
              ResolvedPrice indexPrice = indexPrices.get(override.isin());
              boolean clearedAStop = indexPrice == null && !excludedIsins.contains(override.isin());
              return new UsedPriceOverride(
                  override,
                  clearedAStop ? GateNeed.CLEARED_A_STOP : GateNeed.NOT_NEEDED,
                  indexPrice);
            })
        .toList();
  }
}
