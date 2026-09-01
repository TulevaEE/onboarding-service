package ee.tuleva.onboarding.investment.check.tracking;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

@Slf4j
final class TrackingDifferenceEventMapper {

  private TrackingDifferenceEventMapper() {}

  static Map<String, Object> buildResultMap(TrackingDifferenceResult result) {
    var attributions =
        result.securityAttributions().stream()
            .map(
                a -> {
                  var map = new LinkedHashMap<String, Object>();
                  map.put("isin", a.isin());
                  map.put("modelWeight", Objects.requireNonNullElse(a.modelWeight(), ZERO));
                  map.put("actualWeight", Objects.requireNonNullElse(a.actualWeight(), ZERO));
                  map.put(
                      "weightDifference", Objects.requireNonNullElse(a.weightDifference(), ZERO));
                  map.put("securityReturn", a.securityReturn());
                  map.put("benchmarkReturn", Objects.requireNonNullElse(a.benchmarkReturn(), ZERO));
                  map.put("contribution", a.contribution());
                  return map;
                })
            .toList();

    var navResidualEvaluated = result.navResidual() != null;

    return Map.of(
        "securityAttributions",
        attributions,
        "cashDrag",
        Objects.requireNonNullElse(result.cashDrag(), ZERO),
        "feeDrag",
        Objects.requireNonNullElse(result.feeDrag(), ZERO),
        "residual",
        Objects.requireNonNullElse(result.residual(), ZERO),
        "impliedFundReturn",
        Objects.requireNonNullElse(result.bodImpliedFundReturn(), ZERO),
        "navResidual",
        Objects.requireNonNullElse(result.navResidual(), ZERO),
        "navResidualBreach",
        result.navResidualBreach(),
        "navResidualEvaluated",
        navResidualEvaluated);
  }

  record EventPayload(
      BigDecimal cashDrag,
      BigDecimal feeDrag,
      BigDecimal residual,
      Map<String, BigDecimal> contributionByIsin) {}

  static EventPayload parseEventPayload(Map<String, Object> result) {
    var cashDrag = toBd(result.get("cashDrag"));
    var feeDrag = toBd(result.get("feeDrag"));
    var residual = toBd(result.get("residual"));

    var contributionByIsin = new LinkedHashMap<String, BigDecimal>();
    @SuppressWarnings("unchecked")
    var attrs = (List<Map<String, Object>>) result.getOrDefault("securityAttributions", List.of());
    for (var attr : attrs) {
      var isin = (String) attr.get("isin");
      if (isin == null || isin.isBlank()) {
        continue;
      }
      var contribution = toBd(attr.get("contribution"));
      contributionByIsin.merge(isin, contribution, BigDecimal::add);
    }
    return new EventPayload(cashDrag, feeDrag, residual, contributionByIsin);
  }

  static Map<String, BigDecimal> mergeAttributions(
      Map<String, BigDecimal> prior, List<SecurityAttribution> todayAttrs) {
    var merged = new LinkedHashMap<>(prior);
    if (todayAttrs != null) {
      for (var attr : todayAttrs) {
        if (attr.isin() == null || attr.isin().isBlank()) {
          continue;
        }
        merged.merge(attr.isin(), attr.contribution(), BigDecimal::add);
      }
    }
    return merged;
  }

  private static BigDecimal toBd(@Nullable Object value) {
    if (value == null) return ZERO;
    if (value instanceof BigDecimal bd) return bd;
    if (value instanceof Number n) return new BigDecimal(n.toString());
    if (value instanceof String s && !s.isBlank()) {
      try {
        return new BigDecimal(s);
      } catch (NumberFormatException e) {
        log.warn("Unparseable BigDecimal in JSONB: value={}", s);
        return ZERO;
      }
    }
    if (!(value instanceof String)) {
      log.warn("Unexpected type in JSONB numeric field: type={}", value.getClass().getSimpleName());
    }
    return ZERO;
  }
}
