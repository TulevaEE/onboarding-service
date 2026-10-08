package ee.tuleva.onboarding.mandate.email;

import java.util.List;

public record MissedEmailResend(List<Long> sentBatchIds, List<Long> failedBatchIds) {}
