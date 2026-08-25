package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;

/** A transaction would have taken fund units out of an account that does not hold them. */
public class UnitBalanceViolationException extends RuntimeException {

  public UnitBalanceViolationException(String accountName, BigDecimal held, BigDecimal requested) {
    super(
        "Refusing to take units an account does not hold: account=%s, held=%s, requested=%s"
            .formatted(accountName, held, requested));
  }
}
