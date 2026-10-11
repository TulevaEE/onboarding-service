package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.util.Set;

record Postability(Set<String> knownAccountCodes) {

  boolean allows(JournalEntryPart part) {
    return part.lines().size() >= 2
        && part.lines().stream()
                .map(JournalEntryLine::amount)
                .reduce(ZERO, BigDecimal::add)
                .signum()
            == 0
        && part.lines().stream()
            .map(JournalEntryLine::accountCode)
            .allMatch(knownAccountCodes::contains)
        && part.lines().stream().map(JournalEntryLine::amount).allMatch(Postability::fitsTheLedger);
  }

  private static boolean fitsTheLedger(BigDecimal amount) {
    final int LEDGER_ENTRY_INTEGER_DIGITS = 15;
    var stripped = amount.stripTrailingZeros();
    return stripped.scale() <= EUR.getMaxPrecision()
        && stripped.precision() - stripped.scale() <= LEDGER_ENTRY_INTEGER_DIGITS;
  }
}
