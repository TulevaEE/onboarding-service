package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.PriceValuation.Kind.MANUAL_PRICE;
import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.plain;
import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.putIfPresent;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
sealed interface TradePrepPrice {

  String MANUAL_SOURCE = "MANUAL";

  @Nullable BigDecimal value();

  Map<String, Object> resolution(String isin);

  void describe(Map<String, Object> trade);

  default Optional<RevaluationPrice> revaluation() {
    return Optional.empty();
  }

  record IndexPrice(ResolvedPrice resolved) implements TradePrepPrice {

    @Override
    public @Nullable BigDecimal value() {
      return resolved.usedPrice();
    }

    @Override
    public Map<String, Object> resolution(String isin) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("isin", isin);
      putIfPresent(map, "price", plain(resolved.usedPrice()));
      putIfPresent(
          map, "priceDate", resolved.priceDate() == null ? null : resolved.priceDate().toString());
      putIfPresent(
          map,
          "priceSource",
          resolved.priceSource() == null ? null : resolved.priceSource().name());
      putIfPresent(
          map,
          "validationStatus",
          resolved.validationStatus() == null ? null : resolved.validationStatus().name());
      return map;
    }

    @Override
    public void describe(Map<String, Object> trade) {
      putIfPresent(trade, "price", plain(resolved.usedPrice()));
    }
  }

  record ManualPrice(PriceOverride override) implements TradePrepPrice {

    @Override
    public BigDecimal value() {
      return override.price();
    }

    @Override
    public Map<String, Object> resolution(String isin) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("isin", isin);
      map.put("price", override.price().toPlainString());
      map.put("priceSource", MANUAL_SOURCE);
      map.put("comment", override.comment());
      return map;
    }

    @Override
    public void describe(Map<String, Object> trade) {
      trade.put("price", override.price().toPlainString());
      trade.put("priceSource", MANUAL_SOURCE);
    }

    @Override
    public Optional<RevaluationPrice> revaluation() {
      return Optional.of(new RevaluationPrice(override.price(), MANUAL_PRICE));
    }
  }
}
