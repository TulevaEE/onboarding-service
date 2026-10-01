package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import java.util.Map;

public record GeneralLedgerAccount(
    String code, AccountType accountType, Map<String, Object> metadata) {}
