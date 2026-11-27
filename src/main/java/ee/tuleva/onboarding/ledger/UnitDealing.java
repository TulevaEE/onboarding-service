package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record UnitDealing(
    UUID transactionId,
    TransactionType type,
    Instant bookedAt,
    @Nullable BigDecimal units,
    @Nullable BigDecimal cashAmount,
    @Nullable BigDecimal navPerUnit,
    @Nullable LocalDate navDate) {}
