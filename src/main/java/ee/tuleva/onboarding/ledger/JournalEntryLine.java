package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;

public record JournalEntryLine(String accountCode, BigDecimal amount) {}
