package ee.tuleva.onboarding.savings.fund.notification;

import ee.tuleva.onboarding.party.PartyId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RedemptionRequestedEvent(
    UUID redemptionRequestId,
    long userId,
    PartyId party,
    BigDecimal requestedAmount,
    BigDecimal fundUnits,
    String customerIban,
    Instant requestedAt,
    Instant cancellationDeadline,
    Instant payoutDeadline) {}
