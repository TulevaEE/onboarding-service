package ee.tuleva.onboarding.kyc;

import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.user.User;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KycCheckService {

  private final ApplicationEventPublisher eventPublisher;
  private final KycChecker kycChecker;

  public void check(User subject, Set<Country> countries, KycSurveyPurpose purpose) {
    eventPublisher.publishEvent(new BeforeKycCheckedEvent(subject, countries));
    var kycCheck = kycChecker.check(subject.getId());
    eventPublisher.publishEvent(
        new KycCheckPerformedEvent(this, subject.getPersonalCode(), kycCheck, purpose));
  }
}
