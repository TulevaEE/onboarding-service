package ee.tuleva.onboarding.kyc;

import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.HIGH;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Country;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KycCheckService {

  private final ApplicationEventPublisher eventPublisher;

  public void check(Person person, Country country) {
    eventPublisher.publishEvent(new BeforeKycCheckedEvent(person, country));
    var kycCheck = new KycCheck(99, HIGH); // TODO: replace with actual logic
    eventPublisher.publishEvent(
        new KycCheckPerformedEvent(this, person.getPersonalCode(), kycCheck));
  }
}
