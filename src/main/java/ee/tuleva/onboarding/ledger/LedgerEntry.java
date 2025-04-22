package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record LedgerEntry(
    UUID id,
    BigDecimal amount,
    Currency currency,
    Instant createdTime,
    UUID transactionId,
    UUID accountId) {}
