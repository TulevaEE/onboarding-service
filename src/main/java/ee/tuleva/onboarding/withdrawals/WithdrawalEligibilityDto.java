package ee.tuleva.onboarding.withdrawals;

public record WithdrawalEligibilityDto(
    boolean hasReachedEarlyRetirementAge, int recommendedDurationYears) {}
