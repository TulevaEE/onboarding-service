package ee.tuleva.onboarding.kyc;

import ee.tuleva.onboarding.user.User;

public interface KycChecker {
  KycCheck check(User user);
}
