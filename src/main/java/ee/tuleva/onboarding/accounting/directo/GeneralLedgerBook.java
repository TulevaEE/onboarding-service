package ee.tuleva.onboarding.accounting.directo;

import ee.tuleva.onboarding.ledger.GeneralLedgerAccount;
import ee.tuleva.onboarding.ledger.JournalEntryPart;
import java.util.List;
import java.util.Set;

public record GeneralLedgerBook(
    List<GeneralLedgerAccount> accounts,
    List<JournalEntryPart> parts,
    Set<String> protectedSourceKeys) {}
