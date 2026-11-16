package ee.tuleva.onboarding.aml.alert;

import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * One monthly TKF volume window for one party (person or legal entity), feeding the 15k/30k rules.
 * Separate last-deposit and last-redemption timestamps let the manual-override suppression compare
 * against the matching direction. Legal entities are classified as present, new clients ({@code
 * presentInCrm=true}, {@code existingClient=false}); {@code partyType} drives how the alert message
 * identifies the party.
 */
public record TkfVolumeAggregate(
    String personalId,
    @Nullable BigDecimal depositsThisMonth,
    @Nullable BigDecimal redemptionsThisMonth,
    @Nullable Instant lastDepositThisMonth,
    @Nullable Instant lastRedemptionThisMonth,
    @Nullable String monthKey,
    boolean presentInCrm,
    boolean existingClient,
    @Nullable Instant lastManualReview,
    AlertPartyType partyType) {}
