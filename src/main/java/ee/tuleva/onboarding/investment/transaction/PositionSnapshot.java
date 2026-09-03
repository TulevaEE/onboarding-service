package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record PositionSnapshot(
    @Nullable String isin,
    BigDecimal marketValue,
    @Nullable BigDecimal quantity,
    @Nullable BigDecimal unitPrice) {

  public PositionSnapshot(@Nullable String isin, BigDecimal marketValue) {
    this(isin, marketValue, null, null);
  }
}
