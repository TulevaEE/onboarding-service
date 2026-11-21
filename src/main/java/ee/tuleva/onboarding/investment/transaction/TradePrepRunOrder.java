package ee.tuleva.onboarding.investment.transaction;

import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record TradePrepRunOrder(
    String instrumentIsin, Instant orderTimestamp, @Nullable LocalDate expectedSettlementDate) {}
