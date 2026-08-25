package ee.tuleva.onboarding.banking.seb.reconciliation;

import ee.tuleva.onboarding.banking.BankAccount;
import java.math.BigDecimal;

public record ReconciliationCompletedEvent(
    BankAccount bankAccount, BigDecimal bankBalance, BigDecimal ledgerBalance, boolean matched) {}
