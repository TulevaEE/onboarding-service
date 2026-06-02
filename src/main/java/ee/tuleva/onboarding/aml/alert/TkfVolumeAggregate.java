package ee.tuleva.onboarding.aml.alert;

import java.math.BigDecimal;
import java.time.Instant;

public record TkfVolumeAggregate(
    String personalId,
    BigDecimal depositsThisMonth,
    BigDecimal redemptionsThisMonth,
    Instant lastFlowThisMonth,
    String monthKey,
    BigDecimal depositsThisYear,
    Instant lastDepositThisYear,
    String yearKey,
    boolean presentInCrm,
    boolean existingClient,
    Instant lastManualReview) {}
