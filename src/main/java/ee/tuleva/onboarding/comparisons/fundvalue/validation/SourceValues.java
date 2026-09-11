package ee.tuleva.onboarding.comparisons.fundvalue.validation;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

record SourceValues(InstrumentSource source, Map<LocalDate, BigDecimal> valuesByDate) {

  BigDecimal valueOn(LocalDate date) {
    return requireNonNull(
        valuesByDate.get(date),
        "Missing source value: source=%s, date=%s".formatted(source.name(), date));
  }
}
