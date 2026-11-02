package ee.tuleva.onboarding.investment.fees.ocf;

import java.time.Instant;

public record ExceptionDecision(String decidedBy, String reason, Instant decidedAt) {}
