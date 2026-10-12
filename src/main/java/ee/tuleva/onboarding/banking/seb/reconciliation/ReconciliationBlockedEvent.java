package ee.tuleva.onboarding.banking.seb.reconciliation;

import ee.tuleva.onboarding.banking.BankAccount;
import java.time.LocalDate;

public record ReconciliationBlockedEvent(
    BankAccount bankAccount, LocalDate statementDate, long unclassifiedEntries) {}
