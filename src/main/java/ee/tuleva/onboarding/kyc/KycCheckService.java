package ee.tuleva.onboarding.kyc;

import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.HIGH;

import org.springframework.stereotype.Service;

@Service
public class KycCheckService {

  public KycCheck check(String personalCode) {
    return new KycCheck(99, HIGH);
  }
}
