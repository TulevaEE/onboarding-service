package ee.tuleva.onboarding.kyc;

import ee.tuleva.onboarding.user.User;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TestKycChecker implements KycChecker {

  private static final Runnable NOTHING = () -> {};

  private final KycCheck defaultKycCheck;
  private final Map<Long, KycCheck> kycChecks = new HashMap<>();
  private Runnable whenChecked = NOTHING;

  public void givenKycCheck(Long userId, KycCheck kycCheck) {
    kycChecks.put(userId, kycCheck);
  }

  public void whenChecked(Runnable action) {
    whenChecked = action;
  }

  public void reset() {
    kycChecks.clear();
    whenChecked = NOTHING;
  }

  @Override
  public KycCheck check(User user) {
    var kycCheck = kycChecks.getOrDefault(user.getIdOrThrow(), defaultKycCheck);
    whenChecked.run();
    return kycCheck;
  }
}
