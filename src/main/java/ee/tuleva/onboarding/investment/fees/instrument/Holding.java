package ee.tuleva.onboarding.investment.fees.instrument;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

record Holding(
    BigDecimal units, LocalDate unitsDate, UnitsSource source, @Nullable InstrumentPrice navPrice) {

  Optional<InstrumentPrice> priceInTheNavReport() {
    return Optional.ofNullable(navPrice);
  }
}
