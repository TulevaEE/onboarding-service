package ee.tuleva.onboarding.kyc;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TestKycChecker implements KycChecker {

  private final KycCheck defaultKycCheck;
  private final Map<Long, KycCheck> kycChecks = new HashMap<>();

  public void givenKycCheck(Long userId, KycCheck kycCheck) {
    kycChecks.put(userId, kycCheck);
  }

  public void reset() {
    kycChecks.clear();
  }

  @Override
  public KycCheck check(Long userId) {
    return kycChecks.getOrDefault(userId, defaultKycCheck);
  }
}
