package ee.tuleva.onboarding.investment.check.rebalance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;

record ModelChange(
    LocalDate effectiveDate,
    Map<String, BigDecimal> weightsWhenItTookEffect,
    NavigableMap<LocalDate, Map<String, BigDecimal>> laterVersions) {

  Map<String, BigDecimal> weightsOn(LocalDate date) {
    return Optional.ofNullable(laterVersions.floorEntry(date))
        .map(Map.Entry::getValue)
        .orElse(weightsWhenItTookEffect);
  }
}
