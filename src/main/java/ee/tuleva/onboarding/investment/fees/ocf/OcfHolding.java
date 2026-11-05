package ee.tuleva.onboarding.investment.fees.ocf;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Objects.requireNonNullElse;

import ee.tuleva.onboarding.investment.fees.rate.ApplicableRate;
import ee.tuleva.onboarding.savings.fund.nav.NavAccountLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

record OcfHolding(BigDecimal weight, ApplicableRate rate) {

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
}
