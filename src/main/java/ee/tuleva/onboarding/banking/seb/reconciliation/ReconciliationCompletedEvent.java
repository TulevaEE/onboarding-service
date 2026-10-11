package ee.tuleva.onboarding.banking.seb.reconciliation;

import ee.tuleva.onboarding.banking.BankAccount;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ReconciliationCompletedEvent(
    BankAccount bankAccount,
    LocalDate statementDate,
    BigDecimal bankBalance,
    BigDecimal ledgerBalance,
    boolean matched) {}
