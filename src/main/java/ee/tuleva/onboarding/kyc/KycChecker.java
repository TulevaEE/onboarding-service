package ee.tuleva.onboarding.kyc;

public interface KycChecker {
  KycCheck check(Long userId);
}
