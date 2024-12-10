package ee.tuleva.onboarding.withdrawals;

public record WithdrawalEligibilityDto(
    @Deprecated boolean hasReachedEarlyRetirementAge,
    PillarWithdrawalEligibility pillarWithdrawalEligibility, // TODO rename
    int age,
    int recommendedDurationYears,
    boolean arrestsOrBankruptciesPresent) {
  public record PillarWithdrawalEligibility(boolean second, boolean third) {}
}
