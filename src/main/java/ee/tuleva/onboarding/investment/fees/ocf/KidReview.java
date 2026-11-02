package ee.tuleva.onboarding.investment.fees.ocf;

import java.time.Instant;

public record KidReview(String reviewedBy, String reason, Instant reviewedAt) {}
