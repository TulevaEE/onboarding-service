package ee.tuleva.onboarding.withdrawals;

public record WithdrawalEligibilityDto(
    boolean hasReachedEarlyRetirementAge,
    boolean canWithdrawThirdPillarWithReducedTax,
    int age,
    int recommendedDurationYears,
    boolean arrestsOrBankruptciesPresent) {
  public record PillarWithdrawalEligibility(boolean second, boolean third) {}
}
