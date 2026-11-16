package ee.tuleva.onboarding.aml.alert;

import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record TkfVolumeAggregate(
    String personalId,
    BigDecimal depositsThisMonth,
    BigDecimal redemptionsThisMonth,
    @Nullable Instant lastDepositThisMonth,
    @Nullable Instant lastRedemptionThisMonth,
    String monthKey,
    boolean presentInCrm,
    boolean existingClient,
    @Nullable Instant lastManualReview,
    AlertPartyType partyType) {}
