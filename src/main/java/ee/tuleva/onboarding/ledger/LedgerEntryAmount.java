package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryAmount(UUID transactionId, Instant transactionDate, BigDecimal amount) {}
