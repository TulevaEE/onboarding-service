package ee.tuleva.onboarding.withdrawals;

import lombok.Builder;

@Builder
public record WithdrawalEligibilityDto(
    boolean hasReachedEarlyRetirementAge,
    boolean canWithdrawThirdPillarWithReducedTax,
    int age,
    int recommendedDurationYears,
    boolean arrestsOrBankruptciesPresent) {}
