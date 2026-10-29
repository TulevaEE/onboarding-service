package ee.tuleva.onboarding.investment.fees.ocf;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import ee.tuleva.onboarding.investment.fees.rate.InstrumentRate;
import ee.tuleva.onboarding.savings.fund.nav.NavAccountLine;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

record OcfHolding(BigDecimal weight, InstrumentRate rate) {

  static final int WEIGHT_SCALE = 12;
  private static final String UNIDENTIFIED_HOLDING = "<no isin>";

  static SortedMap<String, BigDecimal> weightsOf(List<NavAccountLine> lines, BigDecimal aum) {
    var weights = new TreeMap<String, BigDecimal>();
    lines.stream()
        .collect(
            Collectors.groupingBy(
                line -> requireNonNullElse(line.accountId(), UNIDENTIFIED_HOLDING),
                Collectors.reducing(ZERO, NavAccountLine::value, BigDecimal::add)))
        .forEach((isin, value) -> weights.put(isin, value.divide(aum, WEIGHT_SCALE, HALF_UP)));
    return weights;
  }

  static List<OcfHolding> of(
      SortedMap<String, BigDecimal> weights, Map<String, InstrumentRate> rates) {
    return weights.entrySet().stream()
        .map(
            weight ->
                new OcfHolding(
                    weight.getValue(),
                    requireNonNull(
                        rates.get(weight.getKey()),
                        "Unrated holding passed the guard: " + weight.getKey())))
        .toList();
  }

  static List<String> unratedIsins(Collection<String> isins, Map<String, InstrumentRate> rates) {
    return isins.stream().filter(isin -> !rates.containsKey(isin)).distinct().toList();
  }
}
