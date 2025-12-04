package ee.tuleva.onboarding.kyc;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.country.Country;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KycCheckService {

  private final ApplicationEventPublisher eventPublisher;
  private final KycChecker kycChecker;

  public void check(AuthenticatedPerson person, Country country) {
    eventPublisher.publishEvent(new BeforeKycCheckedEvent(person, country));
    var kycCheck = kycChecker.check(person.getUserId());
    eventPublisher.publishEvent(
        new KycCheckPerformedEvent(this, person.getPersonalCode(), kycCheck));
  }
}
