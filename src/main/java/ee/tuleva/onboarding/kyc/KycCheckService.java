package ee.tuleva.onboarding.kyc;

import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.HIGH;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.user.address.Address;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KycCheckService {

  private final ApplicationEventPublisher eventPublisher;

  public void check(Person person, Address address) {
    eventPublisher.publishEvent(new BeforeKycCheckedEvent(person, address));
    var kycCheck = new KycCheck(99, HIGH); // TODO: replace with actual logic
    eventPublisher.publishEvent(
        new KycCheckPerformedEvent(this, person.getPersonalCode(), kycCheck));
  }
}
