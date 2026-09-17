package ee.tuleva.onboarding.investment.fees;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record DepotRate(
    BigDecimal annualRate, @Nullable LocalDate tierAnchorDate, @Nullable BigDecimal tierBasis) {

  public static DepotRate flat(BigDecimal annualRate) {
    return new DepotRate(annualRate, null, null);
  }

  public static DepotRate none() {
    return flat(BigDecimal.ZERO);
  }
}
