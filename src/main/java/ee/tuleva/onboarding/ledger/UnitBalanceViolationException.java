package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public class UnitBalanceViolationException extends RuntimeException {
  public UnitBalanceViolationException(
      @Nullable String accountName, BigDecimal held, BigDecimal requested) {
    super(
        "Refusing to take units an account does not hold: account=%s, held=%s, requested=%s"
            .formatted(accountName, held, requested));
  }
}
