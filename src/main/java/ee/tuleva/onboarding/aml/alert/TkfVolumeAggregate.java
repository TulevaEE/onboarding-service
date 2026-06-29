package ee.tuleva.onboarding.aml.alert;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One TKF volume window for one party (person or legal entity). A monthly window carries month sums
 * (year fields zero/blank) and feeds the 15k/30k rules; a yearly window carries the year deposit
 * sum (month fields zero/blank) and feeds the 49k rule. Separate last-deposit and last-redemption
 * timestamps let the manual-override suppression compare against the matching direction. Legal
 * entities are classified as present, new clients ({@code presentInCrm=true}, {@code
 * existingClient=false}); {@code partyType} drives how the alert message identifies the party.
 */
public record TkfVolumeAggregate(
    String personalId,
    BigDecimal depositsThisMonth,
    BigDecimal redemptionsThisMonth,
    Instant lastDepositThisMonth,
    Instant lastRedemptionThisMonth,
    String monthKey,
    BigDecimal depositsThisYear,
    Instant lastDepositThisYear,
    String yearKey,
    boolean presentInCrm,
    boolean existingClient,
    Instant lastManualReview,
    AlertPartyType partyType) {}
