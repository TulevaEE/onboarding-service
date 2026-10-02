package ee.tuleva.onboarding.investment.fees.ocf;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.investment.fees.rate.InstrumentRate;
import ee.tuleva.onboarding.savings.fund.nav.NavAccountLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

record OcfHolding(BigDecimal value, BigDecimal assetsUnderManagement, InstrumentRate rate) {

  private static final int WEIGHT_SCALE = 12;
  private static final String UNIDENTIFIED_HOLDING = "<no isin>";

  static List<OcfHolding> of(
      List<NavAccountLine> lines, Map<String, InstrumentRate> rates, BigDecimal aum) {
    return lines.stream()
        .collect(
            Collectors.groupingBy(
                line -> requireNonNull(line.accountId(), UNIDENTIFIED_HOLDING),
                TreeMap::new,
                Collectors.reducing(ZERO, NavAccountLine::value, BigDecimal::add)))
        .entrySet()
        .stream()
        .map(
            holding ->
                new OcfHolding(
                    holding.getValue(),
                    aum,
                    requireNonNull(
                        rates.get(holding.getKey()),
                        "Unrated holding passed the guard: " + holding.getKey())))
        .toList();
  }

  static List<String> unratedIsins(List<NavAccountLine> lines, Map<String, InstrumentRate> rates) {
    return lines.stream()
        .map(NavAccountLine::accountId)
        .map(isin -> isin == null ? UNIDENTIFIED_HOLDING : isin)
        .filter(isin -> !rates.containsKey(isin))
        .distinct()
        .toList();
  }

  BigDecimal weight() {
    return value.divide(assetsUnderManagement, WEIGHT_SCALE, HALF_UP);
  }
}
