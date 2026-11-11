package ee.tuleva.onboarding.banking.seb.reconciliation;

import static ee.tuleva.onboarding.banking.seb.reconciliation.BankReconciliation.Balance.DIFFERS;
import static ee.tuleva.onboarding.banking.seb.reconciliation.BankReconciliation.Balance.MATCHES;
import static ee.tuleva.onboarding.banking.seb.reconciliation.BankReconciliation.Outcome.BLOCKED;
import static ee.tuleva.onboarding.banking.seb.reconciliation.BankReconciliation.Outcome.MISMATCH;
import static ee.tuleva.onboarding.banking.seb.reconciliation.BankReconciliation.Outcome.RECONCILED;
import static ee.tuleva.onboarding.banking.seb.reconciliation.BankReconciliation.Suspense.CLEAR;
import static ee.tuleva.onboarding.banking.seb.reconciliation.BankReconciliation.Suspense.NOT_CHECKED;
import static ee.tuleva.onboarding.banking.seb.reconciliation.BankReconciliation.Suspense.UNRESOLVED;
import static ee.tuleva.onboarding.rulebook.Condition.not;
import static ee.tuleva.onboarding.rulebook.Condition.or;

import ee.tuleva.onboarding.rulebook.Branch;
import ee.tuleva.onboarding.rulebook.Case;
import ee.tuleva.onboarding.rulebook.Decision;
import ee.tuleva.onboarding.rulebook.Fact;
import ee.tuleva.onboarding.rulebook.Phrase;
import java.util.List;

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

  private static final Fact<Check> BALANCES_DIFFER =
      new Fact<>(
          new Phrase(
              "panga lõppsaldo erineb registri rahakonto saldost päeva lõpus",
              "panga lõppsaldo võrdub registri rahakonto saldoga päeva lõpus",
              "kontrollitakse kogumis-, väljamakse- ja fondikontot",
              "kontroll käivitub, kui eelmise päeva väljavõte on pangast tõmmatud; väljavõtteid"
                  + " tõmmatakse iga 30 minuti järel kella 04:00–23:30",
              "vea korral saadetakse teade Slacki ja kontroll lõpeb veaga"),
          check -> check.balance() == DIFFERS);

  private static final Fact<Check> UNRESOLVED_ENTRIES =
      new Fact<>(
          new Phrase(
              "registris on enne päeva lõppu lahendamata klassifitseerimata pangakandeid",
              "registris ei ole lahendamata klassifitseerimata pangakandeid",
              "klassifitseerimata kandeid kontrollitakse alles siis, kui saldod klapivad"),
          check -> check.suspense() == UNRESOLVED);

  public static final Decision<Check, Outcome> SISEKORD_16_P_1_4_1 =
      new Decision<>(
          "1.4.1–1.4.2",
          "Igaöine pangakontode ja registri rahakontode võrdlus",
          List.of(
              new Branch<>(MISMATCH, "viga: saldod ei klapi", BALANCES_DIFFER),
              new Branch<>(BLOCKED, "viga: võrdlus on blokeeritud", UNRESOLVED_ENTRIES),
              new Branch<>(
                  RECONCILED, "kontroll on korras", not(or(BALANCES_DIFFER, UNRESOLVED_ENTRIES)))),
          List.of(
              new Case<>("saldod erinevad", new Check(DIFFERS, NOT_CHECKED), MISMATCH),
              new Case<>(
                  "saldod klapivad, üks kanne on klassifitseerimata",
                  new Check(MATCHES, UNRESOLVED),
                  BLOCKED),
              new Case<>(
                  "saldod klapivad, kõik kanded on klassifitseeritud",
                  new Check(MATCHES, CLEAR),
                  RECONCILED)));

  private BankReconciliation() {}
}
