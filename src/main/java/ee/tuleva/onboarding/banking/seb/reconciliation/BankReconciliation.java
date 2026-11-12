package ee.tuleva.onboarding.banking.seb.reconciliation;

import static ee.tuleva.onboarding.banking.seb.reconciliation.BankReconciliation.Balance.DIFFERS;
import static ee.tuleva.onboarding.banking.seb.reconciliation.BankReconciliation.Outcome.BLOCKED;
import static ee.tuleva.onboarding.banking.seb.reconciliation.BankReconciliation.Outcome.MISMATCH;
import static ee.tuleva.onboarding.banking.seb.reconciliation.BankReconciliation.Outcome.RECONCILED;
import static ee.tuleva.onboarding.banking.seb.reconciliation.BankReconciliation.Suspense.UNRESOLVED;

public final class BankReconciliation {

  public enum Outcome {
    MISMATCH,
    BLOCKED,
    RECONCILED
  }

  public enum Balance {
    MATCHES,
    DIFFERS
  }

  public enum Suspense {
    CLEAR,
    UNRESOLVED,
    NOT_CHECKED
  }

  public record Check(Balance balance, Suspense suspense) {}

  private BankReconciliation() {}

  public static Outcome outcome(Check check) {
    if (check.balance() == DIFFERS) {
      return MISMATCH;
    }
    return check.suspense() == UNRESOLVED ? BLOCKED : RECONCILED;
  }
}
