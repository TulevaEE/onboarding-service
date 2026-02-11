package ee.tuleva.onboarding.banking.seb.reconciliation;

import ee.tuleva.onboarding.banking.BankAccountType;
import java.math.BigDecimal;

public record ReconciliationCompletedEvent(
    BankAccountType bankAccount,
    BigDecimal bankBalance,
    BigDecimal ledgerBalance,
    boolean matched) {}
