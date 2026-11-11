package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.LOW;

import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.kyc.KycChecker;
import ee.tuleva.onboarding.user.User;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile({"dev", "staging"})
class DevKycChecker implements KycChecker {

  @Override
  public KycCheck check(User user) {
    log.info(
        "KYC risk answered by the aml-service mock, nothing assessed: userId={}, riskLevel={}",
        user.getId(),
        LOW);
    return new KycCheck(
        LOW, Map.of("assessedBy", "the aml-service mock in dev and staging, nothing assessed"));
  }
}
