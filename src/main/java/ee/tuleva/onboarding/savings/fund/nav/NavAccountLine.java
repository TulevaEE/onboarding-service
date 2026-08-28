package ee.tuleva.onboarding.savings.fund.nav;

import java.math.BigDecimal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record NavAccountLine(
    String accountType,
    String accountName,
    @Nullable String accountId,
    @Nullable BigDecimal quantity,
    @Nullable BigDecimal marketPrice,
    @Nullable BigDecimal marketValue) {

  public BigDecimal value() {
    return marketValue == null ? BigDecimal.ZERO : marketValue;
  }

  public BigDecimal units() {
    return quantity == null ? BigDecimal.ZERO : quantity;
  }
}
