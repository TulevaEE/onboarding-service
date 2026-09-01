package ee.tuleva.onboarding.savings;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record PendingRedemption(
    UUID id,
    Instant requestedAt,
    BigDecimal amount,
    String customerIban,
    Instant cancellationDeadline,
    Instant fulfillmentDeadline) {}
